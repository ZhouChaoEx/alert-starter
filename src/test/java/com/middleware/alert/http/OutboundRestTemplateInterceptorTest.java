package com.middleware.alert.http;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundRestTemplateInterceptorTest {

    @Test
    void successIsReportedWithStatusAndQueryStripped() throws Exception {
        AlertPublisher publisher = publisher(true);
        OutboundRestTemplateInterceptor interceptor = interceptor(publisher);

        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://third/api/query?token=secret&id=1"));
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.BAD_GATEWAY);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0],
                (req, body) -> response);

        assertThat(result).isSameAs(response);
        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getTargetUrl()).isEqualTo("http://third/api/query");
        assertThat(event.getHttpStatus()).isEqualTo(502);
        assertThat(event.getHttpMethod()).isEqualTo("GET");
    }

    @Test
    void errorIsReportedAndRethrown() {
        AlertPublisher publisher = publisher(true);
        OutboundRestTemplateInterceptor interceptor = interceptor(publisher);
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
                URI.create("http://third/api/query"));
        IOException boom = new IOException("read timed out");

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0],
                (req, body) -> {
                    throw boom;
                })).isSameAs(boom);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getHttpStatus()).isNull();
        assertThat(event.getExceptionType()).isEqualTo("java.io.IOException");
        assertThat(event.getTargetUrl()).isEqualTo("http://third/api/query");
    }

    @Test
    void disabledPassesThroughWithoutReporting() throws Exception {
        AlertPublisher publisher = publisher(false);
        OutboundRestTemplateInterceptor interceptor = interceptor(publisher);
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://third/api/query"));
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.GATEWAY_TIMEOUT);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0],
                (req, body) -> response);

        assertThat(result).isSameAs(response);
        assertThat(publisher.getQueueSize()).isZero();
    }

    private static OutboundRestTemplateInterceptor interceptor(AlertPublisher publisher) {
        return new OutboundRestTemplateInterceptor(
                new OutboundReporter(publisher), publisher.getConfigProvider());
    }

    private static AlertPublisher publisher(boolean enabled) {
        AlertProperties properties = TestSupport.properties();
        properties.getHttp().getOutbound().setEnabled(enabled);
        return TestSupport.publisher(properties, null);
    }
}
