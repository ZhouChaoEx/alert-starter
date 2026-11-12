package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DedupRegistryTest {

    @Test
    void firstEventIsPublishedAndRepeatsAreSuppressed() {
        AlertProperties properties = TestSupport.properties();
        properties.getDedup().setWindowSeconds(1);
        DedupRegistry registry = new DedupRegistry(
                TestSupport.configProvider(properties), new TestSupport.MutableClock(1000L));

        AlertEvent event = event("fp-1");

        assertThat(registry.firstSeen(event)).isTrue();
        assertThat(registry.firstSeen(event)).isFalse();
        assertThat(registry.firstSeen(event)).isFalse();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void expiredWindowProducesSummaryWithRepeatCount() {
        AlertProperties properties = TestSupport.properties();
        properties.getDedup().setWindowSeconds(1);
        TestSupport.MutableClock clock = new TestSupport.MutableClock(1000L);
        DedupRegistry registry = new DedupRegistry(TestSupport.configProvider(properties), clock);

        AlertEvent event = event("fp-2");
        registry.firstSeen(event);
        registry.firstSeen(event);
        registry.firstSeen(event);

        clock.advance(1500L);
        List<AlertEvent> summaries = registry.sweepExpired();

        assertThat(summaries).hasSize(1);
        AlertEvent summary = summaries.get(0);
        assertThat(summary.getRepeatCount()).isEqualTo(3);
        assertThat(summary.getTitle()).contains("共 3 次");
        assertThat(summary.getFingerprint()).isEqualTo("fp-2");
        assertThat(registry.size()).isZero();
    }

    @Test
    void singleOccurrenceDoesNotProduceSummary() {
        AlertProperties properties = TestSupport.properties();
        properties.getDedup().setWindowSeconds(1);
        TestSupport.MutableClock clock = new TestSupport.MutableClock(1000L);
        DedupRegistry registry = new DedupRegistry(TestSupport.configProvider(properties), clock);

        registry.firstSeen(event("fp-3"));
        clock.advance(1500L);

        assertThat(registry.sweepExpired()).isEmpty();
    }

    private static AlertEvent event(String fingerprint) {
        return AlertEvent.builder()
                .alertType(AlertType.BIZ_EXCEPTION)
                .alertLevel(AlertLevel.ERROR)
                .title("boom")
                .content("boom")
                .fingerprint(fingerprint)
                .build();
    }
}
