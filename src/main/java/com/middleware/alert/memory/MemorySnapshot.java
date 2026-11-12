package com.middleware.alert.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次内存采样的不可变快照。
 *
 * <p>{@code afterGc*} 为 GC 后的使用量（{@link java.lang.management.MemoryPoolMXBean#getCollectionUsage()}），
 * 部分 JVM/内存池不支持时值为 {@code -1}。</p>
 */
public final class MemorySnapshot {

    private final List<Pool> pools;
    private final long heapUsed;
    private final long heapMax;
    private final long heapCommitted;
    private final long gcCount;
    private final long gcTimeMs;
    private final int threadCount;
    private final long sampledAt;

    public MemorySnapshot(List<Pool> pools, long heapUsed, long heapMax, long heapCommitted,
                          long gcCount, long gcTimeMs, int threadCount) {
        this.pools = Collections.unmodifiableList(new ArrayList<Pool>(pools));
        this.heapUsed = heapUsed;
        this.heapMax = heapMax;
        this.heapCommitted = heapCommitted;
        this.gcCount = gcCount;
        this.gcTimeMs = gcTimeMs;
        this.threadCount = threadCount;
        this.sampledAt = System.currentTimeMillis();
    }

    public List<Pool> getPools() {
        return pools;
    }

    public long getHeapUsed() {
        return heapUsed;
    }

    public long getHeapMax() {
        return heapMax;
    }

    public long getHeapCommitted() {
        return heapCommitted;
    }

    public long getGcCount() {
        return gcCount;
    }

    public long getGcTimeMs() {
        return gcTimeMs;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public long getSampledAt() {
        return sampledAt;
    }

    /**
     * 堆内存池快照。
     */
    public static final class Pool {

        private final String name;
        private final long used;
        private final long max;
        private final long committed;
        private final long afterGcUsed;
        private final long afterGcMax;

        public Pool(String name, long used, long max, long committed,
                    long afterGcUsed, long afterGcMax) {
            this.name = name;
            this.used = used;
            this.max = max;
            this.committed = committed;
            this.afterGcUsed = afterGcUsed;
            this.afterGcMax = afterGcMax;
        }

        public String getName() {
            return name;
        }

        public long getUsed() {
            return used;
        }

        public long getMax() {
            return max;
        }

        public long getCommitted() {
            return committed;
        }

        public long getAfterGcUsed() {
            return afterGcUsed;
        }

        public long getAfterGcMax() {
            return afterGcMax;
        }

        /**
         * GC 后使用率。分母优先用 afterGcMax，缺失时回退 max；都不可用时返回 -1。
         */
        public double afterGcRatio() {
            long denominator = afterGcMax > 0 ? afterGcMax : max;
            if (afterGcUsed < 0 || denominator <= 0) {
                return -1D;
            }
            return (double) afterGcUsed / (double) denominator;
        }
    }
}
