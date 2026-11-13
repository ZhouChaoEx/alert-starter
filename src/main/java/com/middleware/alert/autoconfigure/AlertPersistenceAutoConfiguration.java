package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigParser;
import com.middleware.alert.config.AlertConfigProperties;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRepository;
import com.middleware.alert.config.AlertJdbcAccess;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.AlertSink;
import com.middleware.alert.core.AlertWriter;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 告警库独立数据源与写入器。
 *
 * <p>仅当 {@code alert.enabled=true} 时生效，不复用业务数据源。Hikari 使用懒初始化
 * （initializationFailTimeout=-1），数据库暂时不可用不会阻断应用启动。</p>
 *
 * <p>启用告警即要求配置完整：{@code alert.datasource.url}、驱动类、JdbcTemplate/Hikari
 * 缺失都会在启动阶段失败（见 {@link AlertDatasourceValidationAutoConfiguration}），
 * 不再静默降级为“只采集、不落库”。不需要告警的应用把 {@code alert.enabled} 留空即可。</p>
 *
 * <p><b>不向宿主容器暴露 {@code DataSource} / {@code JdbcTemplate} 类型的 bean</b>：
 * 告警连接池与 JdbcTemplate 封装在 {@link AlertJdbcAccess} 里。否则宿主的
 * {@code @ConditionalOnSingleCandidate(DataSource.class)} 会因多出一个非 primary 的
 * {@code alertDataSource} 而失效，{@code transactionManager}、业务 {@code jdbcTemplate}
 * 不再自动装配（xk-common 的 {@code SqlTicketService} 即因此启动失败）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({JdbcTemplate.class, HikariDataSource.class})
@ConditionalOnExpression("${alert.enabled:false}")
public class AlertPersistenceAutoConfiguration {

    @Bean(name = "alertJdbcTemplate")
    @ConditionalOnMissingBean(name = "alertJdbcTemplate")
    public AlertJdbcAccess alertJdbcTemplate(AlertProperties properties) {
        return new AlertJdbcAccess(properties.getDatasource());
    }

    @Bean
    @ConditionalOnMissingBean(AlertSink.class)
    public AlertWriter alertWriter(@Qualifier("alertJdbcTemplate") AlertJdbcAccess jdbcAccess,
                                   AlertProperties properties,
                                   AlertConfigProvider configProvider,
                                   AlertPublisher publisher) {
        return new AlertWriter(jdbcAccess.jdbcTemplate(), properties, configProvider, publisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "alert.config", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertConfigRepository alertConfigRepository(
            @Qualifier("alertJdbcTemplate") AlertJdbcAccess jdbcAccess) {
        return new AlertConfigRepository(jdbcAccess.jdbcTemplate());
    }

    @Bean
    @ConditionalOnProperty(prefix = "alert.config", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertConfigLoader alertConfigLoader(AlertConfigRepository alertConfigRepository,
                                               AlertConfigProvider alertConfigProvider,
                                               AlertProperties properties,
                                               AlertConfigProperties configProperties,
                                               AlertContext context) {
        return new AlertConfigLoader(alertConfigRepository, new AlertConfigParser(),
                alertConfigProvider, properties, configProperties, context.getAppName());
    }
}
