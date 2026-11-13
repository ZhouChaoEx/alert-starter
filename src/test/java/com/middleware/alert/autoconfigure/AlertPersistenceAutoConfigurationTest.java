package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertJdbcAccess;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启用告警必须配置完整：缺 url / 驱动直接启动失败；装配后不污染宿主容器。
 */
class AlertPersistenceAutoConfigurationTest {

    private static final String[] H2_ALERT = {
            "alert.datasource.url=jdbc:h2:mem:alertreg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "alert.datasource.driver-class-name=org.h2.Driver",
            "alert.datasource.username=sa",
            "alert.datasource.password=",
            "alert.datasource.self-check=false"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AlertAutoConfiguration.class,
                    AlertDatasourceValidationAutoConfiguration.class,
                    AlertPersistenceAutoConfiguration.class))
            .withPropertyValues("alert.enabled=true");

    @Test
    void failsWhenUrlMissing() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("alert.datasource.url");
        });
    }

    @Test
    void failsWhenConfiguredDriverMissing() {
        runner.withPropertyValues(
                        "alert.datasource.url=jdbc:kingbase8://127.0.0.1:54321/pv",
                        "alert.datasource.driver-class-name=com.middleware.alert.missing.NoSuchDriver")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("NoSuchDriver");
                });
    }

    @Test
    void bothJdbcDriversAreOnTestClasspath() {
        // 两个驱动都是 optional，对本模块测试可见；业务方按告警库二选一声明
        ClassLoader classLoader = AlertPersistenceAutoConfigurationTest.class.getClassLoader();
        assertThat(ClassUtils.isPresent("com.kingbase8.Driver", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent("com.mysql.cj.jdbc.Driver", classLoader)).isTrue();
    }

    @Test
    void infersKingbaseDriverFromUrl() {
        runner.withPropertyValues(
                        "alert.datasource.url=jdbc:kingbase8://127.0.0.1:54321/pv",
                        "alert.datasource.self-check=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("alertJdbcTemplate");
                });
    }

    @Test
    void infersMysqlDriverFromUrl() {
        runner.withPropertyValues(
                        "alert.datasource.url=jdbc:mysql://127.0.0.1:3306/alert",
                        "alert.datasource.username=alert",
                        "alert.datasource.password=alert",
                        "alert.datasource.self-check=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("alertJdbcTemplate");
                });
    }

    @Test
    void createsPersistenceWhenDriverPresent() {
        runner.withPropertyValues(H2_ALERT)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("alertJdbcTemplate");
                    assertThat(context).hasBean("alertWriter");
                    assertThat(context).hasSingleBean(AlertJdbcAccess.class);
                    // 告警连接池不暴露成 DataSource / JdbcTemplate 类型，避免影响宿主自动装配
                    assertThat(context).doesNotHaveBean("alertDataSource");
                    assertThat(context).doesNotHaveBean(DataSource.class);
                    assertThat(context).doesNotHaveBean(JdbcTemplate.class);
                });
    }

    @Test
    void doesNotPolluteHostTransactionManager() {
        runner.withConfiguration(AutoConfigurations.of(
                        DataSourceTransactionManagerAutoConfiguration.class))
                .withBean("dataSource", DataSource.class,
                        AlertPersistenceAutoConfigurationTest::businessDataSource)
                .withPropertyValues(H2_ALERT)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 宿主只有一个业务 DataSource，@ConditionalOnSingleCandidate 仍满足
                    assertThat(context.getBeansOfType(DataSource.class)).containsOnlyKeys("dataSource");
                    assertThat(context).hasBean("transactionManager");
                });
    }

    @Test
    void doesNotSuppressHostJdbcTemplate() {
        runner.withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
                .withBean("dataSource", DataSource.class,
                        AlertPersistenceAutoConfigurationTest::businessDataSource)
                .withPropertyValues(H2_ALERT)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("jdbcTemplate");
                    assertThat(context.getBeansOfType(JdbcTemplate.class)).containsOnlyKeys("jdbcTemplate");
                });
    }

    @Test
    void skipsPersistenceWhenAlertDisabled() {
        runner.withPropertyValues(
                        "alert.enabled=false",
                        "alert.datasource.url=jdbc:h2:mem:alertdisabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "alert.datasource.driver-class-name=org.h2.Driver")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("alertJdbcTemplate");
                });
    }

    private static DataSource businessDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("business-pool");
        dataSource.setJdbcUrl("jdbc:h2:mem:business;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
