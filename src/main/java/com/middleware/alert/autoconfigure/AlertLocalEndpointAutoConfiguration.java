package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.web.AlertLocalController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 采集侧本地端点装配：{@code POST /alert/refresh/local} 与 {@code GET /alert/effective}。
 *
 * <p>端点放在 <b>alert-starter</b> 而不是 alert-console-starter，目的是让所有接入采集的
 * 业务服务都具备"被远程刷新"和"被远程读取生效值"的能力，管理页面则只需要一个实例引入
 * console-starter。</p>
 *
 * <p>非 Web 应用、或类路径缺少 Spring MVC 时不装配，不影响启动。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, RestController.class})
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter({AlertAutoConfiguration.class, AlertPersistenceAutoConfiguration.class})
public class AlertLocalEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertLocalController alertLocalController(
            ObjectProvider<AlertConfigLoader> loaderProvider,
            AlertConfigProvider configProvider,
            AlertContext alertContext) {
        return new AlertLocalController(loaderProvider, configProvider, alertContext);
    }
}
