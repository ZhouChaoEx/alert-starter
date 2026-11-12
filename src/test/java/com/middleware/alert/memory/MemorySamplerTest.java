package com.middleware.alert.memory;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySamplerTest {

    @Test
    void disabledByDefault() {
        AlertProperties properties = TestSupport.properties();
        AlertPublisher publisher = TestSupport.publisher(properties, null);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                fixed(snapshot(900, 1000, 10, 950)));

        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void oldGenRatioExceededIsPublished() {
        AlertPublisher publisher = publisher(true, 0.85D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                fixed(snapshot(900, 1000, 10, 950)));

        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.OOM_WARNING);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.CRITICAL);
        assertThat(event.getPoolName()).isEqualTo("G1 Old Gen");
        assertThat(event.getTitle()).contains("G1 Old Gen").contains("GC 后使用率");
        assertThat(event.getExtra()).contains("pools").contains("heap").contains("gc");
        assertThat(event.getFingerprint()).hasSize(32);
    }

    @Test
    void belowThresholdIsIgnored() {
        AlertPublisher publisher = publisher(true, 0.85D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                fixed(snapshot(500, 1000, 10, 600)));

        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void risingHeapWithoutGcIsPublished() {
        AlertPublisher publisher = publisher(true, 0.95D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                sequence(snapshot(400, 1000, 10, 400),
                        snapshot(500, 1000, 10, 500),
                        snapshot(600, 1000, 10, 600)));

        sampler.sample(config(publisher));
        sampler.sample(config(publisher));
        assertThat(publisher.getQueueSize()).isZero();

        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        assertThat(publisher.getQueue().peek().getTitle()).contains("连续 3 次上升");
    }

    @Test
    void gcResetsRisingHistory() {
        AlertPublisher publisher = publisher(true, 0.95D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                sequence(snapshot(400, 1000, 10, 400),
                        snapshot(500, 1000, 10, 500),
                        snapshot(300, 1000, 12, 300),
                        snapshot(400, 1000, 12, 400)));

        sampler.sample(config(publisher));
        sampler.sample(config(publisher));
        sampler.sample(config(publisher));
        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void survivorPoolIsIgnored() {
        AlertPublisher publisher = publisher(true, 0.85D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                fixed(snapshot("PS Survivor Space", 970, 1000, 10, 500)));

        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void lowHeapRisingIsIgnored() {
        AlertPublisher publisher = publisher(true, 0.95D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                sequence(snapshot(100, 1000, 10, 100),
                        snapshot(200, 1000, 10, 200),
                        snapshot(300, 1000, 10, 300)));

        sampler.sample(config(publisher));
        sampler.sample(config(publisher));
        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void nonRisingHeapIsIgnored() {
        AlertPublisher publisher = publisher(true, 0.95D, 3);
        MemorySampler sampler = new MemorySampler(publisher, publisher.getConfigProvider(),
                sequence(snapshot(400, 1000, 10, 400),
                        snapshot(400, 1000, 10, 400),
                        snapshot(400, 1000, 10, 400)));

        sampler.sample(config(publisher));
        sampler.sample(config(publisher));
        sampler.sample(config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    private static AlertPublisher publisher(boolean enabled, double ratio, int samples) {
        AlertProperties properties = TestSupport.properties();
        properties.getMemory().setEnabled(enabled);
        properties.getMemory().setOldGenRatio(ratio);
        properties.getMemory().setConsecutiveSamples(samples);
        return TestSupport.publisher(properties, null);
    }

    private static AlertRuntimeConfig config(AlertPublisher publisher) {
        return publisher.getConfigProvider().get();
    }

    private static MemorySnapshot snapshot(long afterGcUsed, long afterGcMax,
                                           long gcCount, long heapUsed) {
        return snapshot("G1 Old Gen", afterGcUsed, afterGcMax, gcCount, heapUsed);
    }

    private static MemorySnapshot snapshot(String poolName, long afterGcUsed, long afterGcMax,
                                           long gcCount, long heapUsed) {
        List<MemorySnapshot.Pool> pools = Arrays.asList(
                new MemorySnapshot.Pool(poolName, afterGcUsed, afterGcMax, afterGcMax,
                        afterGcUsed, afterGcMax));
        return new MemorySnapshot(pools, heapUsed, 1000L, 1000L, gcCount, 100L, 42);
    }

    private static MemoryProbe fixed(MemorySnapshot snapshot) {
        return () -> snapshot;
    }

    private static MemoryProbe sequence(MemorySnapshot... snapshots) {
        final int[] index = {0};
        return () -> snapshots[Math.min(index[0]++, snapshots.length - 1)];
    }
}
