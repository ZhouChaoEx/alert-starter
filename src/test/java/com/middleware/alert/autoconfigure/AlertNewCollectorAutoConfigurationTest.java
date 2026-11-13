package com.middleware.alert.autoconfigure;

import com.middleware.alert.async.AlertAsyncConfigurer;
import com.middleware.alert.async.AsyncExceptionHandler;
import com.middleware.alert.http.FeignOutboundPostProcessor;
import com.middleware.alert.http.OutboundReporter;
import com.middleware.alert.http.OutboundUrlFilter;
import com.middleware.alert.memory.MemorySampler;
import com.middleware.alert.sql.MybatisInterceptorRegistrar;
import com.middleware.alert.sql.SlowSqlInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 新增两类采集器的装配行为。
 */
class AlertNewCollectorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AlertAutoConfiguration.class,
                    AlertSlowSqlAutoConfiguration.class,
                    AlertAsyncAutoConfiguration.class,
                    AlertMemoryAutoConfiguration.class,
                    AlertHttpOutboundAutoConfiguration.class))
            .withPropertyValues("spring.application.name=test-app", "alert.enabled=true");

    @Test
    void registersSlowSqlAndAsyncCollectors() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SlowSqlInterceptor.class);
            assertThat(context).hasSingleBean(MybatisInterceptorRegistrar.class);
            assertThat(context).hasSingleBean(AsyncExceptionHandler.class);
            assertThat(context).hasSingleBean(AlertAsyncConfigurer.class);
            assertThat(context).hasSingleBean(MemorySampler.class);
            assertThat(context).hasSingleBean(OutboundReporter.class);
            assertThat(context).hasSingleBean(OutboundUrlFilter.class);
            assertThat(context).hasSingleBean(FeignOutboundPostProcessor.class);
        });
    }

    @Test
    void slowSqlDegradesWithoutMybatisOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(SqlSessionFactory.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SlowSqlInterceptor.class);
                    assertThat(context).doesNotHaveBean(MybatisInterceptorRegistrar.class);
                });
    }

    @Test
    void asyncConfigurerIsSkippedWhenApplicationDefinesItsOwn() {
        runner.withBean("customAsyncConfigurer", AsyncConfigurer.class,
                        () -> new AsyncConfigurer() {
                            @Override
                            public Executor getAsyncExecutor() {
                                return null;
                            }

                            @Override
                            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                                return null;
                            }
                        })
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncConfigurer.class);
                    assertThat(context).doesNotHaveBean(AlertAsyncConfigurer.class);
                    // 采集器本身仍然存在，业务方可以自行接线
                    assertThat(context).hasSingleBean(AsyncExceptionHandler.class);
                });
    }

    @Test
    void collectorsAreAbsentWhenAlertDisabled() {
        runner.withPropertyValues("alert.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SlowSqlInterceptor.class);
            assertThat(context).doesNotHaveBean(AsyncExceptionHandler.class);
            assertThat(context).doesNotHaveBean(MemorySampler.class);
            assertThat(context).doesNotHaveBean(OutboundReporter.class);
        });
    }
}
