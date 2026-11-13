package com.middleware.alert.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 告警库访问入口：内部持有独立的 Hikari 连接池与 JdbcTemplate。
 *
 * <p>刻意不实现 {@link javax.sql.DataSource}、也不继承 {@link JdbcTemplate}。
 * 这两个类型一旦作为 bean 出现在宿主容器里，Spring Boot 的
 * {@code @ConditionalOnSingleCandidate(DataSource.class)}（{@code transactionManager}）与
 * {@code @ConditionalOnMissingBean(JdbcTemplate.class)}（业务 {@code jdbcTemplate}）
 * 就会失效：例如 xk-common 的 {@code SqlTicketService} 注入 {@code PlatformTransactionManager}
 * 会直接启动失败。因此告警数据源只作为普通 bean 暴露，宿主容器里既看不到第二个
 * {@code DataSource}，也看不到额外的 {@code JdbcTemplate}。</p>
 *
 * <p>Hikari 使用懒初始化（{@code initializationFailTimeout=-1}），数据库不可用不会阻断应用启动。</p>
 */
public class AlertJdbcAccess implements DisposableBean {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public AlertJdbcAccess(AlertProperties.Datasource config) {
        this.dataSource = createDataSource(config);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.jdbcTemplate.setQueryTimeout(3);
    }

    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    @Override
    public void destroy() {
        dataSource.close();
    }

    private static HikariDataSource createDataSource(AlertProperties.Datasource config) {
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
}
