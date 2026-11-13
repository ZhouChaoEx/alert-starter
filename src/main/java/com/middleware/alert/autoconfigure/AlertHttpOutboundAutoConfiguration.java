package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.http.FeignOutboundPostProcessor;
import com.middleware.alert.http.OutboundReporter;
import com.middleware.alert.http.OutboundRestTemplateInterceptor;
import com.middleware.alert.http.OutboundUrlFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 出站 HTTP 采集装配。
 *
 * <p>包含两条链路：</p>
 * <ol>
 *   <li>RestTemplate：通过 {@link RestTemplateCustomizer} 给容器里的 RestTemplate 加拦截器；</li>
 *   <li>Feign：通过 BeanPostProcessor 代理 {@code @FeignClient} 接口。</li>
 * </ol>
 *
 * <p>与其它采集器一致：装配层不判断 {@code http.outbound.enabled}（默认关闭、页面可热开启），
 * 开关由拦截逻辑每次读运行时快照判定。<b>不覆盖</b>业务自建的 HTTP 客户端
 * （{@code new RestTemplate()}、Hutool {@code HttpUtil}、OkHttp 等）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(AlertAutoConfiguration.class)
public class AlertHttpOutboundAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboundUrlFilter alertOutboundUrlFilter() {
        return new OutboundUrlFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundReporter alertOutboundReporter(AlertPublisher publisher,
                                                  OutboundUrlFilter alertOutboundUrlFilter) {
        return new OutboundReporter(publisher, alertOutboundUrlFilter);
    }

    /**
     * 只对 Spring 容器管理的 RestTemplate 生效；业务 {@code new RestTemplate()} 不会被拦截。
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    @ConditionalOnMissingBean(name = "alertOutboundRestTemplateCustomizer")
    public RestTemplateCustomizer alertOutboundRestTemplateCustomizer(
            OutboundReporter alertOutboundReporter, AlertConfigProvider configProvider) {
        return restTemplate -> restTemplate.getInterceptors()
                .add(new OutboundRestTemplateInterceptor(alertOutboundReporter, configProvider));
    }

    /**
     * Feign 链路。必须声明为 static：BeanPostProcessor 在容器早期实例化，
     * 非 static 方法会导致配置类被提前创建，跳过其余条件判断。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignClient")
    @ConditionalOnMissingBean
    public static FeignOutboundPostProcessor alertFeignOutboundPostProcessor(
            ObjectProvider<OutboundReporter> reporterProvider,
            ObjectProvider<AlertConfigProvider> configProviderProvider) {
        return new FeignOutboundPostProcessor(
                reporterProvider.getObject(), configProviderProvider.getObject());
    }
}
