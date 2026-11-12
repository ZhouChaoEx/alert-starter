package com.middleware.alert.web;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class SlowRequestFilterTest {

    @Test
    void publishesWhenRequestExceedsThreshold() throws Exception {
        AlertProperties properties = TestSupport.properties();
        properties.getSlowRequest().setThresholdMs(0L);
        AlertPublisher publisher = TestSupport.publisher(properties, null);
        SlowRequestFilter filter = new SlowRequestFilter(publisher, publisher.getConfigProvider());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
        };

        filter.doFilter(request, response, chain);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event).isNotNull();
        assertThat(event.getAlertType()).isEqualTo(AlertType.SLOW_REQUEST);
        assertThat(event.getRequestUri()).isEqualTo("/api/orders");
        assertThat(event.getHttpMethod()).isEqualTo("GET");
        assertThat(event.getCostMs()).isNotNull();
        assertThat(event.getTraceId()).isNullOrEmpty();
    }

    @Test
    void ignoresConfiguredPaths() throws Exception {
        AlertProperties properties = TestSupport.properties();
        properties.getSlowRequest().setThresholdMs(0L);
        AlertPublisher publisher = TestSupport.publisher(properties, null);
        SlowRequestFilter filter = new SlowRequestFilter(publisher, publisher.getConfigProvider());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void doesNotPublishFastRequest() throws Exception {
        AlertProperties properties = TestSupport.properties();
        properties.getSlowRequest().setThresholdMs(10_000L);
        AlertPublisher publisher = TestSupport.publisher(properties, null);
        SlowRequestFilter filter = new SlowRequestFilter(publisher, publisher.getConfigProvider());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fast");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(publisher.getQueueSize()).isZero();
    }
}
