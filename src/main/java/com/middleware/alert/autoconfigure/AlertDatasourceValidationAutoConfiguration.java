package com.middleware.alert.autoconfigure;

import com.middleware.alert.config.AlertProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 启用告警时的数据源强制校验。
 *
 * <p>{@code alert.enabled=true} 表示业务方要接入告警落库，此时告警库配置必须完整：
 * {@code alert.datasource.url} 为空、类路径缺 JdbcTemplate/Hikari/驱动，都会直接让应用
 * 启动失败，不再静默降级为“只采集、不落库”。不需要告警的应用把 {@code alert.enabled}
 * 留空（默认 false）即可。</p>
 *
 * <p>驱动类名来自配置（{@code alert.datasource.driver-class-name}）；留空时按 JDBC URL
 * 推断（{@code jdbc:kingbase8:} / {@code jdbc:mysql:} / {@code jdbc:postgresql:}），
 * 注解无法表达，故用 {@link ClassUtils} 在启动阶段判断。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlertProperties.class)
@ConditionalOnProperty(prefix = "alert", name = "enabled", havingValue = "true")
public class AlertDatasourceValidationAutoConfiguration {

    @Bean
    public AlertDatasourceValidator alertDatasourceValidator(AlertProperties properties) {
        return new AlertDatasourceValidator(properties);
    }

    static final class AlertDatasourceValidator implements InitializingBean {

        private final AlertProperties properties;

        AlertDatasourceValidator(AlertProperties properties) {
            this.properties = properties;
        }

        @Override
        public void afterPropertiesSet() {
            AlertProperties.Datasource datasource = properties.getDatasource();
            if (!StringUtils.hasText(datasource.getUrl())) {
                throw new IllegalStateException("alert.enabled=true requires alert.datasource.url. "
                        + "Configure the alert database (alert.datasource.url/username/password), "
                        + "or leave alert.enabled unset to disable alert collection.");
            }
            require("org.springframework.jdbc.core.JdbcTemplate", "spring-boot-starter-jdbc");
            require("com.zaxxer.hikari.HikariDataSource", "HikariCP (spring-boot-starter-jdbc)");
            requireDriver(datasource);
        }

        private static void requireDriver(AlertProperties.Datasource datasource) {
            String driverClassName = datasource.getDriverClassName();
            if (!StringUtils.hasText(driverClassName)) {
                driverClassName = inferDriver(datasource.getUrl());
            }
            if (driverClassName == null) {
                throw new IllegalStateException("alert.enabled=true cannot infer the JDBC driver from "
                        + "alert.datasource.url '" + datasource.getUrl() + "'; set "
                        + "alert.datasource.driver-class-name explicitly "
                        + "(com.kingbase8.Driver for Kingbase, com.mysql.cj.jdbc.Driver for MySQL).");
            }
            require(driverClassName.trim(), "the JDBC driver: cn.com.kingbase:kingbase8 (Kingbase) "
                    + "or mysql:mysql-connector-java (MySQL)");
        }

        private static String inferDriver(String url) {
            if (!StringUtils.hasText(url)) {
                return null;
            }
            String lower = url.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("jdbc:kingbase8:")) {
                return "com.kingbase8.Driver";
            }
            if (lower.startsWith("jdbc:mysql:")) {
                return "com.mysql.cj.jdbc.Driver";
            }
            if (lower.startsWith("jdbc:postgresql:")) {
                return "org.postgresql.Driver";
            }
            return null;
        }

        private static void require(String className, String hint) {
            ClassLoader classLoader = AlertDatasourceValidator.class.getClassLoader();
            if (!ClassUtils.isPresent(className, classLoader)) {
                throw new IllegalStateException("alert.enabled=true requires class '" + className
                        + "' on the classpath; add " + hint + ".");
            }
        }
    }
}
