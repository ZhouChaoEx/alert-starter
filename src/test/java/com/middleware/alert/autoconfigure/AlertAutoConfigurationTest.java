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
            .withPropertyValues("spring.application.name=test-app", "alert.enabled=true");

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
    void writerIsCreatedWithDatasourceUrl() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AlertAutoConfiguration.class,
                        AlertPersistenceAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=test-app",
                        "alert.enabled=true",
                        "alert.datasource.url=jdbc:h2:mem:coreurl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "alert.datasource.driver-class-name=org.h2.Driver",
                        "alert.datasource.username=sa",
                        "alert.datasource.password=",
                        "alert.datasource.self-check=false")
                .run(context -> assertThat(context).hasSingleBean(AlertWriter.class));
    }

    @Test
    void webBeansAreRegisteredInServletContext() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AlertAutoConfiguration.class,
                        AlertWebAutoConfiguration.class))
                .withPropertyValues("spring.application.name=test-app", "alert.enabled=true")
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
                        "alert.enabled=true",
                        "alert.biz.enabled=false",
                        "alert.slow-request.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BizExceptionAspect.class);
                    assertThat(context).doesNotHaveBean("alertSlowRequestFilter");
                });
    }
}
