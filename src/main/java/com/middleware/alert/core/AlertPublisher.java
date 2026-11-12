package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警发布器：业务线程侧的唯一入口。
 *
 * <p>职责顺序：限流 → 指纹去重 → 入队。真正写库由 {@link com.middleware.alert.core.AlertSink}
 * 的实现（writer 线程）完成，业务线程不做任何 IO。</p>
 */
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);
    private static final long DROP_LOG_INTERVAL_MS = 10_000L;

    private final AlertProperties properties;
    private final AlertConfigProvider configProvider;
    private final AlertContext context;
    private final FingerprintGenerator fingerprints;
    private final DedupRegistry dedup;
    private final RateLimiter rateLimiter;
    private final ObjectProvider<AlertSink> sinkProvider;
    private final BlockingQueue<AlertEvent> queue;

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicLong suppressed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong enqueued = new AtomicLong();
    private volatile long lastDropLogAt;

    public AlertPublisher(AlertProperties properties,
                          AlertConfigProvider configProvider,
                          AlertContext context,
                          FingerprintGenerator fingerprints,
                          DedupRegistry dedup,
                          RateLimiter rateLimiter,
                          ObjectProvider<AlertSink> sinkProvider) {
        this.properties = properties;
        this.configProvider = configProvider;
        this.context = context;
        this.fingerprints = fingerprints;
        this.dedup = dedup;
        this.rateLimiter = rateLimiter;
        this.sinkProvider = sinkProvider;
        this.queue = new ArrayBlockingQueue<AlertEvent>(
                Math.max(1, properties.getQueue().getCapacity()));
    }

    /**
     * 创建一个预填公共字段的事件构造器，供各采集点使用。
     */
    public AlertEvent.Builder newEvent() {
        return AlertEvent.builder()
                .appName(context.getAppName())
                .env(context.getEnv())
                .instanceIp(context.getInstanceIp())
                .threadName(Thread.currentThread().getName())
                .traceId(currentTraceId());
    }

    /**
     * 发布事件。永不抛出异常。
     */
    public void publish(AlertEvent event) {
        if (event == null || !enabled.get() || !configProvider.get().isRuntimeEnabled()) {
            return;
        }
        try {
            if (event.getAlertType() == null) {
                log.warn("alert event ignored: alertType is null");
                return;
            }
            if (event.getAlertLevel() == null) {
                event = copyWithLevel(event, AlertLevel.ERROR);
            }
            if (event.getFingerprint() == null) {
                event.setFingerprint(fingerprints.of(event.getAlertType(), event.getTitle()));
            }
            if (!rateLimiter.allow(event.getAlertType())) {
                suppressed.incrementAndGet();
                return;
            }
            if (!dedup.firstSeen(event)) {
                suppressed.incrementAndGet();
                return;
            }
            if (configProvider.get().getWriteMode() == AlertProperties.WriteMode.SYNC) {
                writeSync(event);
                return;
            }
            enqueue(event);
        } catch (Throwable t) {
            log.warn("alert publish failed", t);
        }
    }

    /**
     * 由 writer 线程周期调用，把到期的去重窗口汇总事件补发出去。
     */
    public void sweep() {
        try {
            for (AlertEvent summary : dedup.sweepExpired()) {
                enqueue(summary);
            }
        } catch (Throwable t) {
            log.warn("alert dedup sweep failed", t);
        }
    }

    private void writeSync(AlertEvent event) {
        AlertSink sink = sinkProvider.getIfAvailable();
        if (sink == null) {
            log.warn("alert write mode is SYNC but no sink is available, event dropped: {}", event);
            dropped.incrementAndGet();
            return;
        }
        sink.write(Collections.singletonList(event));
    }

    private void enqueue(AlertEvent event) {
        if (!queue.offer(event)) {
            long total = dropped.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastDropLogAt >= DROP_LOG_INTERVAL_MS) {
                lastDropLogAt = now;
                log.warn("alert queue is full (capacity={}), dropped {} events so far",
                        properties.getQueue().getCapacity(), total);
            }
            return;
        }
        enqueued.incrementAndGet();
    }

    private AlertEvent copyWithLevel(AlertEvent source, AlertLevel level) {
        return AlertEvent.builder()
                .appName(source.getAppName())
                .env(source.getEnv())
                .instanceIp(source.getInstanceIp())
                .alertType(source.getAlertType())
                .alertLevel(level)
                .title(source.getTitle())
                .content(source.getContent())
                .exceptionType(source.getExceptionType())
                .stackTrace(source.getStackTrace())
                .requestUri(source.getRequestUri())
                .targetUrl(source.getTargetUrl())
                .httpMethod(source.getHttpMethod())
                .httpStatus(source.getHttpStatus())
                .costMs(source.getCostMs())
                .sqlText(source.getSqlText())
                .mapperId(source.getMapperId())
                .poolName(source.getPoolName())
                .threadName(source.getThreadName())
                .traceId(source.getTraceId())
                .fingerprint(source.getFingerprint())
                .repeatCount(source.getRepeatCount())
                .firstTime(source.getFirstTime())
                .alertTime(source.getAlertTime())
                .extra(source.getExtra())
                .build();
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = MDC.get("X-B3-TraceId");
        }
        return traceId;
    }

    public BlockingQueue<AlertEvent> getQueue() {
        return queue;
    }

    public AlertContext getContext() {
        return context;
    }

    public FingerprintGenerator getFingerprints() {
        return fingerprints;
    }

    public AlertConfigProvider getConfigProvider() {
        return configProvider;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public long getSuppressedCount() {
        return suppressed.get();
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public long getEnqueuedCount() {
        return enqueued.get();
    }

    public int getQueueSize() {
        return queue.size();
    }
}
