package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.AlertWriter;
import com.middleware.alert.core.DedupRegistry;
import com.middleware.alert.core.RateLimiter;
import com.middleware.alert.web.BizExceptionAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AlertAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AlertAutoConfiguration.class))
            .withPropertyValues("spring.application.name=test-app");

    @Test
    void registersCoreBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AlertProperties.class);
            assertThat(context).hasSingleBean(AlertConfigProvider.class);
            assertThat(context).hasSingleBean(AlertContext.class);
            assertThat(context).hasSingleBean(AlertPublisher.class);
            assertThat(context).hasSingleBean(DedupRegistry.class);
            assertThat(context).hasSingleBean(RateLimiter.class);
        });
    }

    @Test
    void disabledByGlobalSwitch() {
        runner.withPropertyValues("alert.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(AlertPublisher.class));
    }

    @Test
    void writerIsNotCreatedWithoutDatasourceUrl() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AlertAutoConfiguration.class,
                        AlertPersistenceAutoConfiguration.class))
                .withPropertyValues("spring.application.name=test-app")
                .run(context -> assertThat(context).doesNotHaveBean(AlertWriter.class));
    }

    @Test
    void webBeansAreRegisteredInServletContext() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AlertAutoConfiguration.class,
                        AlertWebAutoConfiguration.class))
                .withPropertyValues("spring.application.name=test-app")
                .run(context -> {
                    assertThat(context).hasSingleBean(BizExceptionAspect.class);
                    assertThat(context).hasBean("alertSlowRequestFilter");
                });
    }

    @Test
    void webBeansAreAbsentWhenBizDisabled() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AlertAutoConfiguration.class,
                        AlertWebAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=test-app",
                        "alert.biz.enabled=false",
                        "alert.slow-request.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BizExceptionAspect.class);
                    assertThat(context).doesNotHaveBean("alertSlowRequestFilter");
                });
    }
}
