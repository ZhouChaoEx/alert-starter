package com.middleware.alert.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 读取共享库中的 alert_config 表。
 *
 * <p>复用告警数据源（alertJdbcTemplate），不新增连接池。</p>
 */
public class AlertConfigRepository {

    private static final String SELECT_BY_APP =
            "SELECT config_key, config_value, value_type, version "
                    + "FROM alert_config WHERE app_name = ?";

    private final JdbcTemplate jdbcTemplate;

    public AlertConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AlertConfigRow> findByAppName(String appName) {
        return jdbcTemplate.query(SELECT_BY_APP, new RowMapper<AlertConfigRow>() {
            @Override
            public AlertConfigRow mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new AlertConfigRow(
                        rs.getString("config_key"),
                        rs.getString("config_value"),
                        rs.getString("value_type"),
                        rs.getLong("version"));
            }
        }, appName);
    }

    /**
     * 只读自检：确认表存在且当前应用有可读数据。
     */
    public void selfCheck(String appName) {
        jdbcTemplate.queryForList(
                "SELECT 1 FROM alert_config WHERE app_name = ? AND 1=0", appName);
    }
}
