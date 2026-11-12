package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.config.AlertConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指纹去重注册表。
 *
 * <p>同一指纹在窗口内的第一条事件立即返回 {@code true}（调用方立即入队），
 * 后续重复事件只累加计数并抑制。窗口到期后，若累计次数大于 1，
 * {@link #sweepExpired()} 会产出一条汇总事件，避免同一问题刷屏。</p>
 *
 * <p>该类只在业务线程与 writer 线程中访问，线程安全依赖 {@link ConcurrentHashMap#compute}。</p>
 */
public class DedupRegistry {

    private static final Logger log = LoggerFactory.getLogger(DedupRegistry.class);

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final AlertConfigProvider configProvider;
    private final Clock clock;

    public DedupRegistry(AlertConfigProvider configProvider) {
        this(configProvider, Clock.systemDefaultZone());
    }

    public DedupRegistry(AlertConfigProvider configProvider, Clock clock) {
        this.configProvider = configProvider;
        this.clock = clock;
    }

    /**
     * 登记一次事件。
     *
     * @return true 表示窗口内首次出现，调用方应立即入队；false 表示被抑制
     */
    public boolean firstSeen(AlertEvent event) {
        final String fingerprint = event.getFingerprint();
        if (fingerprint == null) {
            return true;
        }
        final long now = clock.millis();
        final boolean[] first = {false};

        entries.compute(fingerprint, (key, existing) -> {
            if (existing == null) {
                first[0] = true;
                return new Entry(event, now);
            }
            existing.count.incrementAndGet();
            existing.lastTime = now;
            return existing;
        });

        evictIfNeeded();
        return first[0];
    }

    /**
     * 扫描并移除到期条目，返回需要补发的汇总事件。
     */
    public List<AlertEvent> sweepExpired() {
        long windowMs = Math.max(1, configProvider.get().getDedupWindowSeconds()) * 1000L;
        long now = clock.millis();
        List<AlertEvent> summaries = new ArrayList<AlertEvent>();

        for (Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Entry> mapEntry = it.next();
            Entry entry = mapEntry.getValue();
            if (now - entry.windowStart < windowMs) {
                continue;
            }
            if (!entries.remove(mapEntry.getKey(), entry)) {
                continue;
            }
            int count = entry.count.get();
            if (count > 1) {
                summaries.add(summary(entry, count));
            }
        }

        if (!summaries.isEmpty()) {
            log.debug("alert dedup window expired, summary events: {}", summaries.size());
        }
        return summaries;
    }

    private AlertEvent summary(Entry entry, int count) {
        AlertEvent first = entry.firstEvent;
        String title = first.getTitle() == null ? "" : first.getTitle();
        String suffix = "（窗口内共 " + count + " 次）";
        if (title.length() + suffix.length() > 512) {
            title = title.substring(0, Math.max(0, 512 - suffix.length()));
        }
        return AlertEvent.builder()
                .appName(first.getAppName())
                .env(first.getEnv())
                .instanceIp(first.getInstanceIp())
                .alertType(first.getAlertType())
                .alertLevel(first.getAlertLevel())
                .title(title + suffix)
                .content(first.getContent())
                .exceptionType(first.getExceptionType())
                .stackTrace(first.getStackTrace())
                .requestUri(first.getRequestUri())
                .targetUrl(first.getTargetUrl())
                .httpMethod(first.getHttpMethod())
                .httpStatus(first.getHttpStatus())
                .costMs(first.getCostMs())
                .sqlText(first.getSqlText())
                .mapperId(first.getMapperId())
                .poolName(first.getPoolName())
                .threadName(first.getThreadName())
                .traceId(first.getTraceId())
                .fingerprint(first.getFingerprint())
                .repeatCount(count)
                .firstTime(first.getFirstTime())
                .alertTime(new Timestamp(entry.lastTime))
                .extra(first.getExtra())
                .build();
    }

    private void evictIfNeeded() {
        int maxEntries = configProvider.get().getDedupMaxEntries();
        if (maxEntries <= 0 || entries.size() <= maxEntries) {
            return;
        }
        // 超限时按窗口开始时间淘汰最旧的一批，避免无界增长
        int target = Math.max(1, maxEntries / 10);
        List<Map.Entry<String, Entry>> snapshot = new ArrayList<Map.Entry<String, Entry>>(entries.entrySet());
        snapshot.sort((a, b) -> Long.compare(a.getValue().windowStart, b.getValue().windowStart));
        for (int i = 0; i < snapshot.size() && i < target; i++) {
            entries.remove(snapshot.get(i).getKey(), snapshot.get(i).getValue());
        }
        log.warn("alert dedup entries exceeded {}, evicted {} oldest entries", maxEntries, target);
    }

    /**
     * 清空去重窗口。供测试或运维手动重置使用。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 仅用于测试与自监控。
     */
    public int size() {
        return entries.size();
    }

    private static final class Entry {

        private final AlertEvent firstEvent;
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(1);
        private volatile long lastTime;

        private Entry(AlertEvent firstEvent, long now) {
            this.firstEvent = firstEvent;
            this.windowStart = now;
            this.lastTime = now;
        }
    }
}
