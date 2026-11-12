package com.middleware.alert.sql;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertPublisher;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlowSqlInterceptorTest {

    private static final Method UPDATE_METHOD = updateMethod();

    private static Method updateMethod() {
        try {
            return Executor.class.getMethod("update", MappedStatement.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void disabledByDefault() throws Throwable {
        AlertPublisher publisher = publisher(false, 1L, Collections.<String>emptyList());

        Object result = newInterceptor(publisher).intercept(invocation("com.x.Mapper.update", "update t set a = 1"));

        assertThat(result).isEqualTo(1);
        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void slowStatementIsPublished() throws Throwable {
        AlertPublisher publisher = publisher(true, 1L, Collections.<String>emptyList());

        newInterceptor(publisher).intercept(
                invocation("com.x.UserMapper.updateById", "update t_user set name = 'a' where id = 1"));

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.SLOW_SQL);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.WARN);
        assertThat(event.getMapperId()).isEqualTo("com.x.UserMapper.updateById");
        assertThat(event.getSqlText()).isEqualTo("update t_user set name = ? where id = ?");
        assertThat(event.getCostMs()).isNotNull().isGreaterThanOrEqualTo(1L);
        assertThat(event.getTitle()).contains("慢 SQL").contains("UserMapper.updateById");
    }

    @Test
    void fastStatementIsIgnored() throws Throwable {
        AlertPublisher publisher = publisher(true, 10_000L, Collections.<String>emptyList());

        newInterceptor(publisher).intercept(invocation("com.x.Mapper.update", "update t set a = 1"));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void excludedMapperPrefixIsIgnored() throws Throwable {
        AlertPublisher publisher = publisher(true, 1L, Collections.singletonList("com.x.audit."));

        newInterceptor(publisher).intercept(
                invocation("com.x.audit.AuditMapper.insert", "insert into audit values (1)"));

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void sameStatementDifferentLiteralsShareFingerprint() throws Throwable {
        AlertPublisher publisher = publisher(true, 1L, Collections.<String>emptyList());
        SlowSqlInterceptor interceptor = newInterceptor(publisher);

        interceptor.intercept(invocation("com.x.Mapper.select", "select * from t where id = 1"));
        String first = publisher.getQueue().peek().getFingerprint();

        AlertPublisher other = publisher(true, 1L, Collections.<String>emptyList());
        newInterceptor(other).intercept(
                invocation("com.x.Mapper.select", "select * from t where id = 999"));
        String second = other.getQueue().peek().getFingerprint();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentMapperProducesDifferentFingerprint() throws Throwable {
        AlertPublisher first = publisher(true, 1L, Collections.<String>emptyList());
        newInterceptor(first).intercept(invocation("com.x.Mapper.select", "select * from t where id = 1"));

        AlertPublisher second = publisher(true, 1L, Collections.<String>emptyList());
        newInterceptor(second).intercept(invocation("com.y.Mapper.select", "select * from t where id = 1"));

        assertThat(first.getQueue().peek().getFingerprint())
                .isNotEqualTo(second.getQueue().peek().getFingerprint());
    }

    private static AlertPublisher publisher(boolean enabled, long thresholdMs,
                                            java.util.List<String> excludePrefixes) {
        AlertProperties properties = TestSupport.properties();
        properties.getSlowSql().setEnabled(enabled);
        properties.getSlowSql().setThresholdMs(thresholdMs);
        properties.getSlowSql().setExcludeMapperPrefixes(excludePrefixes);
        return TestSupport.publisher(properties, null);
    }

    private static SlowSqlInterceptor newInterceptor(AlertPublisher publisher) {
        return new SlowSqlInterceptor(publisher, publisher.getConfigProvider());
    }

    private static Invocation invocation(String mapperId, String sql) throws Exception {
        Executor executor = mock(Executor.class);
        when(executor.update(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(5L);
            return 1;
        });

        // MappedStatement 是 final 类，Mockito 无法 mock，这里用 MyBatis 自带 Builder 构造真实对象
        Configuration configuration = new Configuration();
        SqlSource sqlSource = parameterObject -> new BoundSql(configuration, sql,
                Collections.<ParameterMapping>emptyList(), parameterObject);
        MappedStatement statement = new MappedStatement.Builder(
                configuration, mapperId, sqlSource, SqlCommandType.UPDATE).build();

        return new Invocation(executor, UPDATE_METHOD, new Object[]{statement, new Object()});
    }
}
