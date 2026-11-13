package com.middleware.alert.autoconfigure;

import com.middleware.alert.async.AlertAsyncConfigurer;
import com.middleware.alert.async.AsyncExceptionHandler;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

import java.util.Map;

/**
 * 异步异常采集装配。
 *
 * <p>只有业务方<b>没有</b>自定义 {@link AsyncConfigurer} 时才注册：
 * Spring 只允许一个 {@code AsyncConfigurer}，覆盖业务方的实现会改变其线程池配置。
 * 业务方已自定义时跳过并打 WARN，此时可自行把 {@link AsyncExceptionHandler} 接到自己的
 * {@code getAsyncUncaughtExceptionHandler()} 上（见接入文档）。</p>
 *
 * <p>与慢 SQL 一致：装配层不判断 {@code alert.async.enabled}（默认关闭、可在页面热开启），
 * 开关由 {@link AsyncExceptionHandler} 每次回调时读运行时快照判定。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({AsyncConfigurer.class, AsyncConfigurerSupport.class,
        AsyncUncaughtExceptionHandler.class})
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(AlertAutoConfiguration.class)
public class AlertAsyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertAsyncAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AsyncExceptionHandler alertAsyncExceptionHandler(AlertPublisher publisher,
                                                           AlertConfigProvider configProvider) {
        return new AsyncExceptionHandler(publisher, configProvider);
    }

    @Bean
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    public AlertAsyncConfigurer alertAsyncConfigurer(AsyncExceptionHandler alertAsyncExceptionHandler) {
        return new AlertAsyncConfigurer(alertAsyncExceptionHandler);
    }

    /**
     * 业务方已有 {@code AsyncConfigurer} 时，上面那个 Bean 会被条件跳过且不提示。
     * 这里在容器刷新后检查一次并打 WARN，避免"开关开了但采集不到"的困惑。
     */
    @Bean
    public AlertAsyncConfigurerNotice alertAsyncConfigurerNotice(ApplicationContext applicationContext) {
        return new AlertAsyncConfigurerNotice(applicationContext);
    }

    static class AlertAsyncConfigurerNotice implements InitializingBean {

        private final ApplicationContext applicationContext;

        AlertAsyncConfigurerNotice(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public void afterPropertiesSet() {
            try {
                Map<String, AsyncConfigurer> beans =
                        applicationContext.getBeansOfType(AsyncConfigurer.class);
                // 不能按 instanceof 判断：Sleuth 等组件会把 AsyncConfigurer 包装成 JDK 代理，
                // 代理对象不再是 AlertAsyncConfigurer，会误报"业务已自定义"。
                // 我们的 Bean 名固定为 alertAsyncConfigurer，按名字判断最可靠。
                if (beans.containsKey("alertAsyncConfigurer")) {
                    return;
                }
                log.warn("alert async exception collection is inactive: application already defines "
                        + "AsyncConfigurer ({}), wire AsyncExceptionHandler manually if needed",
                        beans.keySet());
            } catch (Throwable t) {
                log.debug("alert async configurer check skipped: {}", t.toString());
            }
        }
    }
}
