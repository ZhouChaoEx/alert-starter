package com.middleware.alert.http;

import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeignOutboundPostProcessorTest {

    @FeignClient(name = "demo-service", path = "/api")
    interface DemoClient {

        @GetMapping("/users/{id}")
        String getUser(String id);
    }

    @Test
    void proxiesFeignInterfaceAndReportsSuccessfulCall() {
        OutboundReporter reporter = mock(OutboundReporter.class);
        FeignOutboundPostProcessor processor =
                new FeignOutboundPostProcessor(reporter, configProvider());

        DemoClient target = id -> "ok";
        DemoClient proxy = (DemoClient) processor.postProcessAfterInitialization(target, "demoClient");

        assertThat(proxy).isInstanceOf(DemoClient.class);
        assertThat(proxy.getUser("1")).isEqualTo("ok");
        // 成功调用拿不到真实状态码，用 0 表示未知，只参与慢调用判定
        verify(reporter).reportIfNeeded(eq("http://demo-service/api/users/{id}"),
                eq("demo-service"), eq("/api/users/{id}"), eq("GET"),
                eq(0), anyLong(), isNull(), any());
    }

    @Test
    void feignExceptionStatusIsPassedToReporter() {
        OutboundReporter reporter = mock(OutboundReporter.class);
        FeignOutboundPostProcessor processor =
                new FeignOutboundPostProcessor(reporter, configProvider());
        FeignException error = mock(FeignException.class);
        when(error.status()).thenReturn(504);

        DemoClient target = id -> {
            throw error;
        };
        DemoClient proxy = (DemoClient) processor.postProcessAfterInitialization(target, "demoClient");

        assertThatThrownBy(() -> proxy.getUser("1")).isSameAs(error);
        verify(reporter).reportIfNeeded(anyString(), anyString(), anyString(), anyString(),
                eq(504), anyLong(), same(error), any());
    }

    @Test
    void nonFeignBeanIsReturnedUnchanged() {
        OutboundReporter reporter = mock(OutboundReporter.class);
        FeignOutboundPostProcessor processor =
                new FeignOutboundPostProcessor(reporter, configProvider());
        Object bean = new Object();

        assertThat(processor.postProcessAfterInitialization(bean, "bean")).isSameAs(bean);
        verifyNoInteractions(reporter);
    }

    @Test
    void nullBeanIsReturnedAsNull() {
        OutboundReporter reporter = mock(OutboundReporter.class);
        FeignOutboundPostProcessor processor =
                new FeignOutboundPostProcessor(reporter, configProvider());

        assertThat(processor.postProcessAfterInitialization(null, "null")).isNull();
    }

    private static AlertConfigProvider configProvider() {
        AlertProperties properties = TestSupport.properties();
        properties.getHttp().getOutbound().setEnabled(true);
        return TestSupport.configProvider(properties);
    }
}
