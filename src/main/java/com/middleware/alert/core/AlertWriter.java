package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步批量写入器：单线程消费队列，批量 INSERT 到 alert_info。
 *
 * <p>失败重试 3 次（100/500/2000ms），仍失败则整批丢弃并计数，不落文件、不阻塞业务。
 * 数据源不可用时只打 WARN，应用启动与业务请求不受影响。</p>
 */
public class AlertWriter implements AlertSink, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AlertWriter.class);

    private static final String INSERT_SQL =
            "INSERT INTO alert_info ("
                    + "app_name, env, instance_ip, alert_type, alert_level, title, content, "
                    + "exception_type, stack_trace, request_uri, target_url, http_method, "
                    + "http_status, cost_ms, sql_text, mapper_id, pool_name, thread_name, "
                    + "trace_id, fingerprint, repeat_count, first_time, alert_time, create_time, extra"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final long[] RETRY_BACKOFF_MS = {100L, 500L, 2000L};
    private static final long SWEEP_INTERVAL_MS = 5_000L;

    private final JdbcTemplate jdbcTemplate;
    private final AlertProperties properties;
    private final AlertConfigProvider configProvider;
    private final AlertPublisher publisher;
    private final BlockingQueue<AlertEvent> queue;

    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failedBatches = new AtomicLong();

    private volatile boolean running;
    private volatile Thread worker;
    private volatile long lastSweepAt;
    private volatile boolean selfChecked;

    public AlertWriter(JdbcTemplate jdbcTemplate,
                       AlertProperties properties,
                       AlertConfigProvider configProvider,
                       AlertPublisher publisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.configProvider = configProvider;
        this.publisher = publisher;
        this.queue = publisher.getQueue();
    }

    @Override
    public void write(List<AlertEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Timestamp createTime = new Timestamp(System.currentTimeMillis());
        List<Object[]> args = new ArrayList<Object[]>(events.size());
        for (AlertEvent event : events) {
            event.setCreateTime(createTime);
            args.add(toArgs(event));
        }
        writeWithRetry(events.size(), args);
    }

    private void writeWithRetry(int size, List<Object[]> args) {
        Exception lastError = null;
        for (int attempt = 0; attempt <= RETRY_BACKOFF_MS.length; attempt++) {
            try {
                jdbcTemplate.batchUpdate(INSERT_SQL, args);
                written.addAndGet(size);
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt < RETRY_BACKOFF_MS.length) {
                    sleepQuietly(RETRY_BACKOFF_MS[attempt]);
                }
            }
        }
        failedBatches.incrementAndGet();
        dropped.addAndGet(size);
        log.warn("alert batch write failed, dropped {} events: {}",
                size, lastError == null ? "unknown" : lastError.getMessage(), lastError);
    }

    private Object[] toArgs(AlertEvent event) {
        return new Object[]{
                truncate(event.getAppName(), 128),
                truncate(event.getEnv(), 32),
                truncate(event.getInstanceIp(), 64),
                event.getAlertType() == null ? null : event.getAlertType().name(),
                event.getAlertLevel() == null ? null : event.getAlertLevel().name(),
                truncate(event.getTitle(), 512),
                truncate(event.getContent(), 8192),
                truncate(event.getExceptionType(), 256),
                event.getStackTrace(),
                truncate(event.getRequestUri(), 512),
                truncate(event.getTargetUrl(), 512),
                truncate(event.getHttpMethod(), 16),
                event.getHttpStatus(),
                event.getCostMs(),
                event.getSqlText(),
                truncate(event.getMapperId(), 256),
                truncate(event.getPoolName(), 128),
                truncate(event.getThreadName(), 128),
                truncate(event.getTraceId(), 64),
                truncate(event.getFingerprint(), 64),
                event.getRepeatCount(),
                event.getFirstTime(),
                event.getAlertTime(),
                event.getCreateTime(),
                truncate(event.getExtra(), 4096)
        };
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loop() {
        try {
            selfCheck();
            while (running || !queue.isEmpty()) {
                AlertRuntimeConfig config = configProvider.get();
                List<AlertEvent> batch = new ArrayList<AlertEvent>(
                        Math.max(1, config.getBatchSize()));
                AlertEvent first = queue.poll(
                        Math.max(1L, config.getBatchIntervalMs()), TimeUnit.MILLISECONDS);
                if (first == null) {
                    sweepIfNeeded();
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, Math.max(0, config.getBatchSize() - 1));
                write(batch);
                sweepIfNeeded();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.warn("alert writer loop terminated unexpectedly", t);
        } finally {
            flushRemaining();
        }
    }

    private void sweepIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastSweepAt < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepAt = now;
        publisher.sweep();
    }

    private void flushRemaining() {
        if (queue.isEmpty()) {
            return;
        }
        List<AlertEvent> remaining = new ArrayList<AlertEvent>(queue.size());
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("alert writer flushing {} remaining events", remaining.size());
            write(remaining);
        }
    }

    private void selfCheck() {
        if (selfChecked || !properties.getDatasource().isSelfCheck()) {
            return;
        }
        selfChecked = true;
        try {
            jdbcTemplate.queryForList("SELECT 1 FROM alert_info WHERE 1=0");
            log.info("alert table self-check passed");
        } catch (Exception e) {
            log.warn("alert table self-check failed, check datasource and table alert_info: {}",
                    e.getMessage());
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::loop, "alert-writer");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
        AlertRuntimeConfig config = configProvider.get();
        log.info("alert writer started, queue capacity={}, batch size={}, interval={}ms",
                properties.getQueue().getCapacity(),
                config.getBatchSize(),
                config.getBatchIntervalMs());
    }

    @Override
    public void stop() {
        running = false;
        Thread thread = this.worker;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(5_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("alert writer stopped, written={}, dropped={}, failedBatches={}",
                written.get(), dropped.get(), failedBatches.get());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 先启动、后停止：业务组件停止后仍能 flush 队列
        return Integer.MIN_VALUE + 100;
    }

    public long getWrittenCount() {
        return written.get();
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public long getFailedBatches() {
        return failedBatches.get();
    }
}
