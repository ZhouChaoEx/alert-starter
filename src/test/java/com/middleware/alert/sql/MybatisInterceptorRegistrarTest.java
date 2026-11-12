package com.middleware.alert.sql;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisInterceptorRegistrarTest {

    private final SlowSqlInterceptor interceptor = mock(SlowSqlInterceptor.class);
    private final MybatisInterceptorRegistrar registrar =
            new MybatisInterceptorRegistrar(interceptor);

    @Test
    void registersInterceptorOnSqlSessionFactory() {
        Configuration configuration = new Configuration();
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        when(factory.getConfiguration()).thenReturn(configuration);

        registrar.postProcessAfterInitialization(factory, "sqlSessionFactory");

        assertThat(configuration.getInterceptors()).containsExactly(interceptor);
    }

    @Test
    void sameConfigurationIsRegisteredOnlyOnce() {
        Configuration configuration = new Configuration();
        SqlSessionFactory first = mock(SqlSessionFactory.class);
        SqlSessionFactory second = mock(SqlSessionFactory.class);
        when(first.getConfiguration()).thenReturn(configuration);
        when(second.getConfiguration()).thenReturn(configuration);

        registrar.postProcessAfterInitialization(first, "sqlSessionFactory");
        registrar.postProcessAfterInitialization(second, "sqlSessionFactory2");

        assertThat(configuration.getInterceptors()).containsExactly(interceptor);
    }

    @Test
    void nonSqlSessionFactoryBeanIsReturnedUnchanged() {
        Object bean = new Object();

        assertThat(registrar.postProcessAfterInitialization(bean, "someBean")).isSameAs(bean);
    }

    @Test
    void failureInOneFactoryDoesNotBreakStartup() {
        SqlSessionFactory broken = mock(SqlSessionFactory.class);
        when(broken.getConfiguration()).thenThrow(new IllegalStateException("boom"));

        assertThat(registrar.postProcessAfterInitialization(broken, "broken")).isSameAs(broken);
    }
}
