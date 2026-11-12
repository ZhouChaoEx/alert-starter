package com.middleware.alert.memory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@code java.lang.management} 的内存快照，只读，无额外依赖。
 */
public class JvmMemoryProbe implements MemoryProbe {

    @Override
    public MemorySnapshot snapshot() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory == null ? null : memory.getHeapMemoryUsage();

        List<MemorySnapshot.Pool> pools = new ArrayList<MemorySnapshot.Pool>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool == null || pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage usage = pool.getUsage();
            MemoryUsage afterGc = pool.getCollectionUsage();
            pools.add(new MemorySnapshot.Pool(
                    pool.getName(),
                    usage == null ? -1L : usage.getUsed(),
                    usage == null ? -1L : usage.getMax(),
                    usage == null ? -1L : usage.getCommitted(),
                    afterGc == null ? -1L : afterGc.getUsed(),
                    afterGc == null ? -1L : afterGc.getMax()));
        }

        long gcCount = 0L;
        long gcTimeMs = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc == null) {
                continue;
            }
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
            }
            if (gc.getCollectionTime() > 0) {
                gcTimeMs += gc.getCollectionTime();
            }
        }

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        return new MemorySnapshot(pools,
                heap == null ? -1L : heap.getUsed(),
                heap == null ? -1L : heap.getMax(),
                heap == null ? -1L : heap.getCommitted(),
                gcCount, gcTimeMs,
                threads == null ? -1 : threads.getThreadCount());
    }
}
