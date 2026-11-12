package com.middleware.alert.sql;

import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

/**
 * 慢 SQL 采集：拦截 MyBatis {@code Executor} 的 query/update，超过阈值时上报。
 *
 * <p>开关每执行一次判定一次（读运行时快照），所以在管理页面开启后无需重启即可生效。
 * 开关关闭时只多一次 boolean 判断，不产生任何额外对象。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    private final AlertPublisher publisher;
    private final AlertConfigProvider configProvider;

    public SlowSqlInterceptor(AlertPublisher publisher, AlertConfigProvider configProvider) {
        this.publisher = publisher;
        this.configProvider = configProvider;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        AlertRuntimeConfig config = configProvider.get();
        if (config == null || !config.isSlowSqlEnabled()) {
            return invocation.proceed();
        }
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                report(invocation, cost, config);
            } catch (Throwable t) {
                // 采集失败绝不影响业务
                log.warn("alert slow sql report failed: {}", t.toString());
            }
        }
    }

    private void report(Invocation invocation, long cost, AlertRuntimeConfig config) {
        if (cost < config.getSlowSqlThresholdMs()) {
            return;
        }
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement)) {
            return;
        }
        MappedStatement statement = (MappedStatement) args[0];
        String mapperId = statement.getId();
        if (excluded(mapperId, config.getSlowSqlExcludeMapperPrefixes())) {
            return;
        }

        String sql = null;
        try {
            BoundSql boundSql = statement.getBoundSql(args.length > 1 ? args[1] : null);
            sql = boundSql == null ? null : boundSql.getSql();
        } catch (Exception e) {
            log.debug("alert slow sql bound sql unavailable, mapperId={}: {}", mapperId, e.toString());
        }
        String normalized = SqlNormalizer.normalize(sql);
        String sqlText = truncate(normalized, config.getSlowSqlMaxSqlLength());

        publisher.publish(publisher.newEvent()
                .alertType(AlertType.SLOW_SQL)
                .alertLevel(AlertLevel.WARN)
                .title(truncate("慢 SQL: " + mapperId + " " + cost + "ms", 512))
                .content("执行耗时 " + cost + "ms，超过阈值 " + config.getSlowSqlThresholdMs() + "ms")
                .mapperId(mapperId)
                .sqlText(sqlText)
                .costMs(cost)
                .fingerprint(publisher.getFingerprints()
                        .of(AlertType.SLOW_SQL, mapperId + "|" + normalized))
                .build());
    }

    private static boolean excluded(String mapperId, List<String> prefixes) {
        if (mapperId == null || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.trim().isEmpty() && mapperId.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无外部属性
    }
}
