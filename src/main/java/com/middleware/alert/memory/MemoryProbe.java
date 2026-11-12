package com.middleware.alert.memory;

/**
 * 内存快照采集入口，便于测试替换。
 */
public interface MemoryProbe {

    MemorySnapshot snapshot();
}
