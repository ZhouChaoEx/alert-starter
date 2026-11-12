package com.middleware.alert.memory;

import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 内存 / OOM 前兆采样。
 *
 * <p>守护线程 {@code alert-monitor-memory} 每 {@link #TICK_MS} 唤醒一次，
 * 按运行时快照里的 {@code memory.interval-ms} 决定是否真正采样；
 * 开关为 {@code memory.enabled}（默认关闭），每次采样前判定，页面开启后免重启。</p>
 *
 * <p>判据（任一命中即上报 {@link AlertType#OOM_WARNING}）：</p>
 * <ol>
 *   <li>某个堆内存池 <b>GC 后</b>使用率 {@code collectionUsage.used / max >= memory.old-gen-ratio}；</li>
 *   <li>堆使用量连续 {@code memory.consecutive-samples} 次严格上升，且期间 GC 次数未增加。</li>
 * </ol>
 *
 * <p>只读 {@code java.lang.management}，不产生业务开销；上报走既有去重窗口，
 * 同一内存池在 {@code dedup.window-seconds} 内只告警一次。</p>
 */
public class MemorySampler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MemorySampler.class);

    /** 调度粒度：实际采样间隔由配置决定，这里只是检查频率 */
    private static final long TICK_MS = 10_000L;
    private static final long MIN_INTERVAL_MS = 5_000L;
    private static final int MIN_CONSECUTIVE_SAMPLES = 2;
    /** 连续上升判据的最低堆水位：低于该比例说明堆还很空，上升属正常预热，不应告警 */
    private static final double RISE_MIN_HEAP_RATIO = 0.5D;

    private final AlertPublisher publisher;
    private final AlertConfigProvider configProvider;
    private final MemoryProbe probe;

    /** 供"连续上升"判据使用的最近堆使用量 */
    private final Deque<Long> recentHeapUsed = new ArrayDeque<Long>();

    private volatile long lastSampleAt;
    private volatile long lastGcCount = -1L;
    private volatile boolean running;
    private ScheduledExecutorService executor;

    public MemorySampler(AlertPublisher publisher, AlertConfigProvider configProvider) {
        this(publisher, configProvider, new JvmMemoryProbe());
    }

    public MemorySampler(AlertPublisher publisher, AlertConfigProvider configProvider,
                         MemoryProbe probe) {
        this.publisher = publisher;
        this.configProvider = configProvider;
        this.probe = probe;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "alert-monitor-memory");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        running = true;
        log.info("alert memory sampler started, tick={}ms", TICK_MS);
    }

    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
            log.info("alert memory sampler stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 调度线程入口：开关与间隔都从运行时快照读取，开关关闭时几乎零开销。
     */
    void tick() {
        try {
            AlertRuntimeConfig config = configProvider.get();
            if (config == null || !config.isMemoryEnabled()) {
                return;
            }
            long interval = Math.max(MIN_INTERVAL_MS, config.getMemoryIntervalMs());
            long now = System.currentTimeMillis();
            if (now - lastSampleAt < interval) {
                return;
            }
            lastSampleAt = now;
            sample(config);
        } catch (Throwable t) {
            // 采样失败绝不影响业务
            log.warn("alert memory sample failed: {}", t.toString());
        }
    }

    /**
     * 立即采样一次（忽略间隔判断），供调度线程与单测调用。
     *
     * <p>仍然检查开关：{@code memory.enabled=false} 时什么都不做，
     * 保证"默认关闭"语义在直接调用时也成立。</p>
     */
    void sample(AlertRuntimeConfig config) {
        if (config == null || !config.isMemoryEnabled()) {
            return;
        }
        MemorySnapshot snapshot = probe.snapshot();
        String reason = evaluate(snapshot, config);
        if (reason == null) {
            return;
        }
        publish(snapshot, config, reason);
    }

    /**
     * 判定是否触发，返回触发原因；不触发返回 null。
     */
    private String evaluate(MemorySnapshot snapshot, AlertRuntimeConfig config) {
        MemorySnapshot.Pool oldGen = oldGenPool(snapshot);
        double ratio = oldGen == null ? -1D : oldGen.afterGcRatio();
        if (ratio >= 0D && ratio >= config.getMemoryOldGenRatio()) {
            return "内存池 " + oldGen.getName() + " GC 后使用率 " + percent(ratio)
                    + " 超过阈值 " + percent(config.getMemoryOldGenRatio());
        }

        // 连续上升判据：期间未发生 GC 才累积；堆水位过低时属正常预热，直接跳过
        boolean noGc = lastGcCount < 0 || snapshot.getGcCount() == lastGcCount;
        if (!noGc) {
            recentHeapUsed.clear();
        }
        if (snapshot.getHeapUsed() >= 0) {
            recentHeapUsed.addLast(snapshot.getHeapUsed());
        }
        lastGcCount = snapshot.getGcCount();

        int need = Math.max(MIN_CONSECUTIVE_SAMPLES, config.getMemoryConsecutiveSamples());
        while (recentHeapUsed.size() > need) {
            recentHeapUsed.removeFirst();
        }
        double heapRatio = heapRatio(snapshot);
        if (heapRatio >= RISE_MIN_HEAP_RATIO
                && recentHeapUsed.size() >= need
                && strictlyIncreasing(recentHeapUsed)) {
            return "堆使用量连续 " + need + " 次上升且未发生 GC（当前 " + percent(heapRatio) + "）";
        }
        return null;
    }

    private void publish(MemorySnapshot snapshot, AlertRuntimeConfig config, String reason) {
        MemorySnapshot.Pool oldGen = oldGenPool(snapshot);
        String poolName = oldGen == null ? "heap" : oldGen.getName();

        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> pools = new ArrayList<Map<String, Object>>();
        for (MemorySnapshot.Pool pool : snapshot.getPools()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", pool.getName());
            item.put("used", pool.getUsed());
            item.put("max", pool.getMax());
            item.put("committed", pool.getCommitted());
            item.put("afterGcUsed", pool.getAfterGcUsed());
            item.put("afterGcMax", pool.getAfterGcMax());
            pools.add(item);
        }
        extra.put("pools", pools);

        Map<String, Object> heap = new LinkedHashMap<String, Object>();
        heap.put("used", snapshot.getHeapUsed());
        heap.put("max", snapshot.getHeapMax());
        heap.put("committed", snapshot.getHeapCommitted());
        extra.put("heap", heap);

        Map<String, Object> gc = new LinkedHashMap<String, Object>();
        gc.put("count", snapshot.getGcCount());
        gc.put("timeMs", snapshot.getGcTimeMs());
        extra.put("gc", gc);

        extra.put("threads", snapshot.getThreadCount());
        extra.put("reason", reason);

        publisher.publish(publisher.newEvent()
                .alertType(AlertType.OOM_WARNING)
                .alertLevel(AlertLevel.CRITICAL)
                .title(truncate("内存告警: " + reason, 512))
                .content(reason)
                .poolName(poolName)
                .extra(JsonUtils.toJson(extra))
                .fingerprint(publisher.getFingerprints().of(AlertType.OOM_WARNING, poolName))
                .build());
    }

    /**
     * 取老年代池：优先名字含 Old / Tenured 的堆池；找不到时退回第一个非 Eden/Survivor 的堆池。
     *
     * <p><b>Eden 与 Survivor 绝不能参与判定</b>：GC 后 Eden 会被清空，而 Survivor 因
     * From/To 复制语义长期接近满载（97%~100% 属正常状态），用它们判断内存压力必然误报。</p>
     */
    private static MemorySnapshot.Pool oldGenPool(MemorySnapshot snapshot) {
        MemorySnapshot.Pool fallback = null;
        for (MemorySnapshot.Pool pool : snapshot.getPools()) {
            String name = pool.getName() == null ? "" : pool.getName().toLowerCase(Locale.ROOT);
            if (name.contains("survivor") || name.contains("eden")) {
                continue;
            }
            if (name.contains("old") || name.contains("tenured")) {
                return pool;
            }
            if (fallback == null) {
                fallback = pool;
            }
        }
        return fallback;
    }

    private static boolean strictlyIncreasing(Deque<Long> values) {
        Long previous = null;
        for (Long value : values) {
            if (previous != null && value <= previous) {
                return false;
            }
            previous = value;
        }
        return values.size() >= MIN_CONSECUTIVE_SAMPLES;
    }

    private static double heapRatio(MemorySnapshot snapshot) {
        if (snapshot.getHeapMax() <= 0 || snapshot.getHeapUsed() < 0) {
            return -1D;
        }
        return (double) snapshot.getHeapUsed() / (double) snapshot.getHeapMax();
    }

    private static String percent(double ratio) {
        if (ratio < 0) {
            return "unknown";
        }
        return Math.round(ratio * 100D) + "%";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
