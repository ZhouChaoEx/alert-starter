package com.middleware.alert.config;

/**
 * alert_config 表的一行配置。
 */
public class AlertConfigRow {

    private final String configKey;
    private final String configValue;
    private final String valueType;
    private final long version;

    public AlertConfigRow(String configKey, String configValue, String valueType, long version) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.valueType = valueType;
        this.version = version;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "AlertConfigRow{" +
                "configKey='" + configKey + '\'' +
                ", configValue='" + configValue + '\'' +
                ", valueType='" + valueType + '\'' +
                ", version=" + version +
                '}';
    }
}
