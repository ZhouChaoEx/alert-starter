package com.middleware.alert.sql;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 把 {@link SlowSqlInterceptor} 挂到每一个 {@link SqlSessionFactory} 上。
 *
 * <p>业务方通常用 {@code SqlSessionFactoryBean}（或 MyBatis-Plus 的同类）产出工厂，
 * 这里在 Bean 初始化后统一追加拦截器；同一 {@link Configuration} 只注册一次，
 * 多数据源场景不会重复挂载。任何异常只打 WARN，不影响应用启动。</p>
 */
public class MybatisInterceptorRegistrar implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(MybatisInterceptorRegistrar.class);

    private final SlowSqlInterceptor interceptor;
    private final Set<Configuration> registered =
            Collections.newSetFromMap(new IdentityHashMap<Configuration, Boolean>());

    public MybatisInterceptorRegistrar(SlowSqlInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof SqlSessionFactory)) {
            return bean;
        }
        try {
            Configuration configuration = ((SqlSessionFactory) bean).getConfiguration();
            if (configuration != null && registered.add(configuration)) {
                configuration.addInterceptor(interceptor);
                log.info("alert slow-sql interceptor registered, bean={}", beanName);
            }
        } catch (Throwable t) {
            log.warn("alert slow-sql interceptor register failed, bean={}: {}", beanName, t.toString());
        }
        return bean;
    }
}
