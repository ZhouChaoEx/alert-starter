package com.middleware.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置表加载相关开关，前缀 {@code alert.config}。
 */
@ConfigurationProperties(prefix = "alert.config")
public class AlertConfigProperties {

    /** 是否从 alert_config 表加载配置；false 时只用 AlertProperties */
    private boolean enabled = true;

    /** 启动时是否检查 alert_config 表是否存在，失败只告警 */
    private boolean selfCheck = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSelfCheck() {
        return selfCheck;
    }

    public void setSelfCheck(boolean selfCheck) {
        this.selfCheck = selfCheck;
    }
}
