package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AlertPublisherTest {

    @Test
    void publishesFirstOccurrenceAndSuppressesRepeats() {
        AlertProperties properties = TestSupport.properties();
        properties.getQueue().setCapacity(10);
        AlertPublisher publisher = TestSupport.publisher(properties, null);

        AlertEvent event = event(publisher, "fp-1");

        publisher.publish(event);
        publisher.publish(event);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        assertThat(publisher.getEnqueuedCount()).isEqualTo(1);
        assertThat(publisher.getSuppressedCount()).isEqualTo(1);
    }

    @Test
    void dropsEventWhenQueueIsFull() {
        AlertProperties properties = TestSupport.properties();
        properties.getQueue().setCapacity(1);
        AlertPublisher publisher = TestSupport.publisher(properties, null);

        publisher.publish(event(publisher, "fp-1"));
        publisher.publish(event(publisher, "fp-2"));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        assertThat(publisher.getDroppedCount()).isEqualTo(1);
    }

    @Test
    void syncModeWritesThroughSinkImmediately() {
        AlertProperties properties = TestSupport.properties();
        properties.setWriteMode(AlertProperties.WriteMode.SYNC);
        AlertSink sink = mock(AlertSink.class);
        AlertPublisher publisher = TestSupport.publisher(properties, sink);

        publisher.publish(event(publisher, "fp-sync"));

        verify(sink).write(anyList());
        assertThat(publisher.getQueueSize()).isZero();
    }

    private static AlertEvent event(AlertPublisher publisher, String fingerprint) {
        return publisher.newEvent()
                .alertType(AlertType.BIZ_EXCEPTION)
                .alertLevel(AlertLevel.ERROR)
                .title("boom")
                .content("boom")
                .fingerprint(fingerprint)
                .build();
    }
}
