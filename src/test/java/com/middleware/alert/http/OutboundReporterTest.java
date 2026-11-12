package com.middleware.alert.http;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundReporterTest {

    @Test
    void disabledDoesNothing() {
        AlertPublisher publisher = publisher(false, 3000L, true);

        reporter(publisher).reportIfNeeded("http://third/x", "third", "/x", "GET", 504, 10L,
                null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void statusHitIsPublishedAsError() {
        AlertPublisher publisher = publisher(true, 3000L, true);

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "GET", 504, 120L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.HTTP_OUTBOUND);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(event.getTargetUrl()).isEqualTo("http://third/api/query");
        assertThat(event.getHttpStatus()).isEqualTo(504);
        assertThat(event.getHttpMethod()).isEqualTo("GET");
        assertThat(event.getCostMs()).isEqualTo(120L);
        assertThat(event.getTitle()).contains("504").contains("GET");
        assertThat(event.getFingerprint()).hasSize(32);
    }

    @Test
    void callErrorIsPublishedAsError() {
        AlertPublisher publisher = publisher(true, 3000L, true);
        IOException error = new IOException("read timed out");

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "POST", -1, 2000L, error, config(publisher));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(event.getHttpStatus()).isNull();
        assertThat(event.getExceptionType()).isEqualTo("java.io.IOException");
        assertThat(event.getCostMs()).isEqualTo(2000L);
        assertThat(event.getTitle()).contains("异常");
    }

    @Test
    void slowCallIsPublishedAsWarn() {
        AlertPublisher publisher = publisher(true, 3000L, true);

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "GET", 200, 3500L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.WARN);
        assertThat(event.getTitle()).contains("慢调用");
        assertThat(event.getHttpStatus()).isEqualTo(200);
    }

    @Test
    void slowCallIgnoredWhenSlowDisabled() {
        AlertPublisher publisher = publisher(true, 3000L, false);

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "GET", 200, 3500L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void fastSuccessIsIgnored() {
        AlertPublisher publisher = publisher(true, 3000L, true);

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "GET", 200, 10L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void nonConfiguredStatusIsIgnored() {
        AlertPublisher publisher = publisher(true, 3000L, true);

        reporter(publisher).reportIfNeeded("http://third/api/query", "third", "/api/query",
                "GET", 500, 10L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void excludedHostIsIgnored() {
        AlertPublisher publisher = publisherWithExclude(
                Collections.singletonList("*.internal.com"), null);

        reporter(publisher).reportIfNeeded("http://a.internal.com/api", "a.internal.com", "/api",
                "GET", 504, 10L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void excludedPathIsIgnored() {
        AlertPublisher publisher = publisherWithExclude(null,
                Collections.singletonList("/actuator/**"));

        reporter(publisher).reportIfNeeded("http://third/actuator/health", "third",
                "/actuator/health", "GET", 504, 10L, null, config(publisher));

        assertThat(publisher.getQueueSize()).isZero();
    }

    private static AlertPublisher publisher(boolean enabled, long threshold, boolean slowEnabled) {
        AlertProperties properties = TestSupport.properties();
        properties.getHttp().getOutbound().setEnabled(enabled);
        properties.getHttp().getOutbound().setThresholdMs(threshold);
        properties.getHttp().getOutbound().setSlowEnabled(slowEnabled);
        return TestSupport.publisher(properties, null);
    }

    private static AlertPublisher publisherWithExclude(java.util.List<String> hosts,
                                                       java.util.List<String> paths) {
        AlertProperties properties = TestSupport.properties();
        properties.getHttp().getOutbound().setEnabled(true);
        if (hosts != null) {
            properties.getHttp().getOutbound().setExcludeHosts(hosts);
        }
        if (paths != null) {
            properties.getHttp().getOutbound().setExcludePaths(paths);
        }
        return TestSupport.publisher(properties, null);
    }

    private static AlertRuntimeConfig config(AlertPublisher publisher) {
        return publisher.getConfigProvider().get();
    }

    private static OutboundReporter reporter(AlertPublisher publisher) {
        return new OutboundReporter(publisher);
    }
}
