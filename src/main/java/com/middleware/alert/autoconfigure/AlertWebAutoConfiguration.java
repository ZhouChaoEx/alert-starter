package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.web.AlertExceptionHandler;
import com.middleware.alert.web.BizExceptionAspect;
import com.middleware.alert.web.SlowRequestFilter;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Web 采集装配：Controller 异常切面 + 慢请求过滤器。
 *
 * <p>非 Web 应用、或类路径缺少 Spring MVC 时整体不生效，不影响启动。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, RestController.class, Aspect.class})
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(AlertAutoConfiguration.class)
public class AlertWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "alert.biz", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertExceptionHandler alertExceptionHandler(AlertPublisher publisher,
                                                       AlertConfigProvider configProvider) {
        return new AlertExceptionHandler(publisher, configProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "alert.biz", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public BizExceptionAspect alertBizExceptionAspect(AlertExceptionHandler alertExceptionHandler) {
        return new BizExceptionAspect(alertExceptionHandler);
    }

    @Bean
    @ConditionalOnMissingBean(name = "alertSlowRequestFilter")
    @ConditionalOnProperty(prefix = "alert.slow-request", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SlowRequestFilter> alertSlowRequestFilter(
            AlertPublisher publisher, AlertConfigProvider configProvider) {
        FilterRegistrationBean<SlowRequestFilter> registration =
                new FilterRegistrationBean<SlowRequestFilter>();
        registration.setFilter(new SlowRequestFilter(publisher, configProvider));
        registration.setName("alertSlowRequestFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
