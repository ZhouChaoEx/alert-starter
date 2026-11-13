package com.middleware.alert.autoconfigure;

import com.middleware.alert.AlertReporter;
import com.middleware.alert.config.AlertConfigProperties;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.AlertSink;
import com.middleware.alert.core.DedupRegistry;
import com.middleware.alert.core.FingerprintGenerator;
import com.middleware.alert.core.RateLimiter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 告警采集核心装配。仅注册内存侧组件，不做任何 IO。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AlertProperties.class, AlertConfigProperties.class})
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AlertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertContext alertContext(AlertProperties properties, Environment environment) {
        return new AlertContext(properties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public FingerprintGenerator alertFingerprintGenerator(AlertContext context) {
        return new FingerprintGenerator(context);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertConfigProvider alertConfigProvider(AlertProperties properties) {
        return new AlertConfigProvider(AlertRuntimeConfig.from(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public DedupRegistry alertDedupRegistry(AlertConfigProvider configProvider) {
        return new DedupRegistry(configProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter alertRateLimiter(AlertConfigProvider configProvider) {
        return new RateLimiter(configProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertPublisher alertPublisher(AlertProperties properties,
                                         AlertConfigProvider configProvider,
                                         AlertContext context,
                                         FingerprintGenerator fingerprintGenerator,
                                         DedupRegistry dedupRegistry,
                                         RateLimiter rateLimiter,
                                         ObjectProvider<AlertSink> sinkProvider) {
        AlertPublisher publisher = new AlertPublisher(properties, configProvider, context, fingerprintGenerator,
                dedupRegistry, rateLimiter, sinkProvider);
        AlertReporter.bind(publisher);
        return publisher;
    }

    @Bean
    public DisposableBean alertReporterCleanup() {
        return AlertReporter::unbind;
    }
}
