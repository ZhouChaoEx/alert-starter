package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigParser;
import com.middleware.alert.config.AlertConfigProperties;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRepository;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.AlertSink;
import com.middleware.alert.core.AlertWriter;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 告警库独立数据源与写入器。
 *
 * <p>仅当配置了 {@code alert.datasource.url} 时生效；不复用业务数据源。
 * Hikari 使用懒初始化（initializationFailTimeout=-1），数据库不可用不会阻断应用启动。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({JdbcTemplate.class, HikariDataSource.class})
@ConditionalOnProperty(prefix = "alert.datasource", name = "url")
public class AlertPersistenceAutoConfiguration {

    @Bean(name = "alertDataSource", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "alertDataSource")
    public HikariDataSource alertDataSource(AlertProperties properties) {
        AlertProperties.Datasource config = properties.getDatasource();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("alert-pool");
        dataSource.setJdbcUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        if (config.getDriverClassName() != null && !config.getDriverClassName().trim().isEmpty()) {
            dataSource.setDriverClassName(config.getDriverClassName().trim());
        }
        dataSource.setMaximumPoolSize(config.getHikari().getMaximumPoolSize());
        dataSource.setMinimumIdle(config.getHikari().getMinimumIdle());
        dataSource.setConnectionTimeout(config.getHikari().getConnectionTimeoutMs());
        // 懒初始化：数据源不可用时不在启动阶段抛异常
        dataSource.setInitializationFailTimeout(-1L);
        dataSource.setAutoCommit(true);
        return dataSource;
    }

    @Bean(name = "alertJdbcTemplate")
    @ConditionalOnMissingBean(name = "alertJdbcTemplate")
    public JdbcTemplate alertJdbcTemplate(@Qualifier("alertDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(3);
        return jdbcTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(AlertSink.class)
    public AlertWriter alertWriter(@Qualifier("alertJdbcTemplate") JdbcTemplate jdbcTemplate,
                                   AlertProperties properties,
                                   AlertConfigProvider configProvider,
                                   AlertPublisher publisher) {
        return new AlertWriter(jdbcTemplate, properties, configProvider, publisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "alert.config", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public AlertConfigRepository alertConfigRepository(
            @Qualifier("alertJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new AlertConfigRepository(jdbcTemplate);
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
