package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.sql.MybatisInterceptorRegistrar;
import com.middleware.alert.sql.SlowSqlInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 慢 SQL 采集装配。
 *
 * <p>只要类路径存在 MyBatis 就装配拦截器，<b>不在装配层判断开关</b>：
 * {@code alert.slow-sql.enabled} 默认关闭且可在管理页面热开启，
 * 若在装配层按 properties 判断，页面开启后必须重启才生效。
 * 真正的开关由 {@link SlowSqlInterceptor} 每次执行时读运行时快照判定。</p>
 *
 * <p>类路径没有 MyBatis（{@code @ConditionalOnClass} 不满足）时整体不生效，不影响启动。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({SqlSessionFactory.class, Interceptor.class})
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(AlertAutoConfiguration.class)
public class AlertSlowSqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SlowSqlInterceptor alertSlowSqlInterceptor(AlertPublisher publisher,
                                                      AlertConfigProvider configProvider) {
        return new SlowSqlInterceptor(publisher, configProvider);
    }

    /**
     * 必须声明为 static：BeanPostProcessor 会在容器早期实例化，
     * 非 static 方法会导致配置类被提前创建，跳过其余条件判断。
     */
    @Bean
    @ConditionalOnMissingBean
    public static MybatisInterceptorRegistrar alertMybatisInterceptorRegistrar(
            ObjectProvider<SlowSqlInterceptor> interceptorProvider) {
        return new MybatisInterceptorRegistrar(interceptorProvider.getObject());
    }
}
