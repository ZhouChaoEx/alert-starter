package com.middleware.alert.it;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.config.AlertJdbcAccess;
import com.middleware.alert.core.DedupRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端接入验证（MySQL 模式）。
 *
 * <p>与 {@link AlertEndToEndTest} 相同，但用 H2 的 MySQL 兼容模式 + MySQL 版建表脚本，
 * 覆盖“告警库为 MySQL”时的完整链路：装配、切面、慢请求 filter、批量落库、配置加载。</p>
 */
@SpringBootTest(
        classes = AlertIntegrationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=alert-it",
                "alert.enabled=true",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.cloud.openfeign.FeignAutoConfiguration",
                "alert.datasource.url=jdbc:h2:mem:alertitmysql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "alert.datasource.driver-class-name=org.h2.Driver",
                "alert.datasource.username=sa",
                "alert.datasource.password=",
                "alert.datasource.self-check=false",
                "alert.batch.interval-ms=50",
                "alert.queue.capacity=1000",
                "alert.rate-limit.per-minute=1000",
                "alert.slow-request.threshold-ms=50",
                "alert.slow-request.ignore-paths=/actuator/**"
        }
)
@Sql(scripts = "/schema-h2-mysql.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Import(AlertEndToEndMysqlTest.TestDataSourceConfig.class)
class AlertEndToEndMysqlTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    @Qualifier("alertJdbcTemplate")
    private AlertJdbcAccess alertJdbcAccess;

    private JdbcTemplate jdbcTemplate() {
        return alertJdbcAccess.jdbcTemplate();
    }

    @Autowired
    private DedupRegistry dedupRegistry;

    @Autowired
    private AlertConfigLoader configLoader;

    @Autowired
    private AlertConfigProvider configProvider;

    @BeforeEach
    void resetDedupWindow() {
        // 去重窗口是内存状态，不会随建表脚本重置，测试间必须显式清理
        dedupRegistry.clear();
    }

    @Test
    void bizExceptionIsPersistedEvenWhenAdviceReturns200() throws Exception {
        ResponseEntity<Map> response = restTemplate.getForEntity("/it/biz", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", "500");

        List<Map<String, Object>> rows = awaitRows("BIZ_EXCEPTION", 1);
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("app_name")).isEqualTo("alert-it");
        assertThat(row.get("alert_level")).isEqualTo("ERROR");
        assertThat(String.valueOf(row.get("title"))).contains("[E100]").contains("order failed");
        assertThat(row.get("request_uri")).isEqualTo("/it/biz");
        assertThat(row.get("http_method")).isEqualTo("GET");
        assertThat(row.get("exception_type"))
                .isEqualTo("com.middleware.alert.exception.AlertableException");
        assertThat(String.valueOf(row.get("fingerprint"))).hasSize(32);
        assertThat(row.get("first_time")).isNotNull();
        assertThat(row.get("alert_time")).isNotNull();
        assertThat(row.get("create_time")).isNotNull();
        assertThat(row.get("trace_id")).isNull();
    }

    @Test
    void nullPointerIsPersisted() throws Exception {
        restTemplate.getForEntity("/it/npe", Map.class);

        List<Map<String, Object>> rows = awaitRows("NULL_POINTER", 1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("alert_level")).isEqualTo("ERROR");
        assertThat(rows.get(0).get("exception_type")).isEqualTo(NullPointerException.class.getName());
    }

    @Test
    void slowRequestIsPersisted() throws Exception {
        restTemplate.getForEntity("/it/slow", String.class);

        List<Map<String, Object>> rows = awaitRows("SLOW_REQUEST", 1);
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(((Number) row.get("cost_ms")).longValue()).isGreaterThanOrEqualTo(150L);
        assertThat(row.get("request_uri")).isEqualTo("/it/slow");
        assertThat(row.get("alert_level")).isEqualTo("WARN");
    }

    @Test
    void repeatedExceptionIsDeduplicatedWithinWindow() throws Exception {
        restTemplate.getForEntity("/it/biz", Map.class);
        restTemplate.getForEntity("/it/biz", Map.class);

        Thread.sleep(1500L);

        Integer count = jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM alert_info WHERE alert_type = 'BIZ_EXCEPTION'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void runtimeConfigFromTableTakesEffectAfterRefresh() throws Exception {
        try {
            jdbcTemplate().update("DELETE FROM alert_config WHERE app_name = ?", "alert-it");
            jdbcTemplate().update(
                    "INSERT INTO alert_config (app_name, config_key, config_value, value_type, version) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    "alert-it", "slow-request.threshold-ms", "1", "LONG", 10L);

            AlertConfigRefreshResult result = configLoader.refresh();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getVersion()).isEqualTo(10L);
            assertThat(result.getChangedKeys()).contains("slow-request.threshold-ms");
            assertThat(configProvider.get().getSlowRequestThresholdMs()).isEqualTo(1L);

            restTemplate.getForEntity("/it/slow", String.class);

            List<Map<String, Object>> rows = awaitRows("SLOW_REQUEST", 1);
            assertThat(rows).hasSize(1);
            assertThat(((Number) rows.get(0).get("cost_ms")).longValue()).isGreaterThanOrEqualTo(150L);
        } finally {
            jdbcTemplate().update("DELETE FROM alert_config WHERE app_name = ?", "alert-it");
            configLoader.refresh();
        }
    }

    private List<Map<String, Object>> awaitRows(String alertType, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        List<Map<String, Object>> rows = Collections.emptyList();
        while (System.currentTimeMillis() < deadline) {
            rows = jdbcTemplate().queryForList(
                    "SELECT * FROM alert_info WHERE alert_type = ? ORDER BY id", alertType);
            if (rows.size() >= expected) {
                return rows;
            }
            Thread.sleep(50L);
        }
        return rows;
    }

    /**
     * {@code @Sql} 需要容器里有一个 DataSource；告警数据源不暴露成该类型，这里补一个业务库。
     */
    @TestConfiguration
    static class TestDataSourceConfig {

        @Bean(name = "dataSource")
        public DataSource dataSource() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:alertitmysql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
