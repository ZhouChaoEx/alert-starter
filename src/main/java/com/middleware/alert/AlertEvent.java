package com.middleware.alert;

import java.sql.Timestamp;

/**
 * 统一告警事件，字段与 alert_info 表一一对应。
 *
 * <p>除 {@code createTime} 由写入线程在落库前统一填充外，其余字段通过
 * {@link Builder} 构造后不再修改。</p>
 */
public class AlertEvent {

    private String appName;
    private String env;
    private String instanceIp;
    private AlertType alertType;
    private AlertLevel alertLevel;
    private String title;
    private String content;
    private String exceptionType;
    private String stackTrace;
    private String requestUri;
    private String targetUrl;
    private String httpMethod;
    private Integer httpStatus;
    private Long costMs;
    private String sqlText;
    private String mapperId;
    private String poolName;
    private String threadName;
    private String traceId;
    private String fingerprint;
    private int repeatCount = 1;
    private Timestamp firstTime;
    private Timestamp alertTime;
    private Timestamp createTime;
    private String extra;

    public AlertEvent() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAppName() {
        return appName;
    }

    public String getEnv() {
        return env;
    }

    public String getInstanceIp() {
        return instanceIp;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Long getCostMs() {
        return costMs;
    }

    public String getSqlText() {
        return sqlText;
    }

    public String getMapperId() {
        return mapperId;
    }

    public String getPoolName() {
        return poolName;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public Timestamp getFirstTime() {
        return firstTime;
    }

    public Timestamp getAlertTime() {
        return alertTime;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    /**
     * 仅供写入线程在批量落库前统一填充，业务采集侧不要调用。
     */
    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    /**
     * 仅供发布器在指纹缺失时补充，采集侧应通过 builder 传入。
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getExtra() {
        return extra;
    }

    @Override
    public String toString() {
        return "AlertEvent{" +
                "alertType=" + alertType +
                ", alertLevel=" + alertLevel +
                ", title='" + title + '\'' +
                ", appName='" + appName + '\'' +
                ", requestUri='" + requestUri + '\'' +
                ", fingerprint='" + fingerprint + '\'' +
                '}';
    }

    public static final class Builder {

        private final AlertEvent event = new AlertEvent();

        private Builder() {
        }

        public Builder appName(String value) {
            event.appName = value;
            return this;
        }

        public Builder env(String value) {
            event.env = value;
            return this;
        }

        public Builder instanceIp(String value) {
            event.instanceIp = value;
            return this;
        }

        public Builder alertType(AlertType value) {
            event.alertType = value;
            return this;
        }

        public Builder alertLevel(AlertLevel value) {
            event.alertLevel = value;
            return this;
        }

        public Builder title(String value) {
            event.title = value;
            return this;
        }

        public Builder content(String value) {
            event.content = value;
            return this;
        }

        public Builder exceptionType(String value) {
            event.exceptionType = value;
            return this;
        }

        public Builder stackTrace(String value) {
            event.stackTrace = value;
            return this;
        }

        public Builder requestUri(String value) {
            event.requestUri = value;
            return this;
        }

        public Builder targetUrl(String value) {
            event.targetUrl = value;
            return this;
        }

        public Builder httpMethod(String value) {
            event.httpMethod = value;
            return this;
        }

        public Builder httpStatus(Integer value) {
            event.httpStatus = value;
            return this;
        }

        public Builder costMs(Long value) {
            event.costMs = value;
            return this;
        }

        public Builder sqlText(String value) {
            event.sqlText = value;
            return this;
        }

        public Builder mapperId(String value) {
            event.mapperId = value;
            return this;
        }

        public Builder poolName(String value) {
            event.poolName = value;
            return this;
        }

        public Builder threadName(String value) {
            event.threadName = value;
            return this;
        }

        public Builder traceId(String value) {
            event.traceId = value;
            return this;
        }

        public Builder fingerprint(String value) {
            event.fingerprint = value;
            return this;
        }

        public Builder repeatCount(int value) {
            event.repeatCount = value;
            return this;
        }

        public Builder firstTime(Timestamp value) {
            event.firstTime = value;
            return this;
        }

        public Builder alertTime(Timestamp value) {
            event.alertTime = value;
            return this;
        }

        public Builder extra(String value) {
            event.extra = value;
            return this;
        }

        public AlertEvent build() {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (event.firstTime == null) {
                event.firstTime = now;
            }
            if (event.alertTime == null) {
                event.alertTime = now;
            }
            return event;
        }
    }
}
