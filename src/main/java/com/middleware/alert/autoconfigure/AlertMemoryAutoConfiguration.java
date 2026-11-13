package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.memory.MemorySampler;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内存 / OOM 前兆采集装配。
 *
 * <p>与慢 SQL、异步异常一致：<b>装配层不判断 {@code alert.memory.enabled}</b>。
 * 该开关默认关闭且可在管理页面热开启，若在装配层按 properties 判断，
 * 页面开启后必须重启才能生效。真正的开关由 {@link MemorySampler} 每次采样前读运行时快照判定。</p>
 *
 * <p>采样只读 JDK 的 {@code java.lang.management}，不引入任何额外依赖。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(AlertAutoConfiguration.class)
public class AlertMemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MemorySampler alertMemorySampler(AlertPublisher publisher,
                                            AlertConfigProvider configProvider) {
        return new MemorySampler(publisher, configProvider);
    }
}
