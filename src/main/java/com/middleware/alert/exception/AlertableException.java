package com.middleware.alert.exception;

import com.middleware.alert.AlertLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务可告警异常。抛出该异常（含子类）后，alert-starter 会自动写入 alert_info。
 *
 * <pre>
 * throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL");
 * </pre>
 */
public class AlertableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final AlertLevel level;
    private final Map<String, Object> extra;

    public AlertableException(String message) {
        this(message, null, null, AlertLevel.ERROR, null, null);
    }

    public AlertableException(String message, String code) {
        this(message, code, null, AlertLevel.ERROR, null, null);
    }

    public AlertableException(String message, String code, AlertLevel level) {
        this(message, code, null, level, null, null);
    }

    public AlertableException(String message, Throwable cause) {
        this(message, null, cause, AlertLevel.ERROR, null, null);
    }

    public AlertableException(String message, String code, Throwable cause) {
        this(message, code, cause, AlertLevel.ERROR, null, null);
    }

    public AlertableException(String message, String code, Throwable cause, AlertLevel level) {
        this(message, code, cause, level, null, null);
    }

    public AlertableException(String message, String code, Throwable cause,
                              AlertLevel level, Map<String, Object> extra) {
        this(message, code, cause, level, extra, null);
    }

    /**
     * 完整构造器。
     *
     * @param message 异常信息
     * @param code    业务错误码，可为空
     * @param cause   原始异常，可为空
     * @param level   告警级别，为空时按 ERROR 处理
     * @param extra   附加信息，需由调用方保证已脱敏
     * @param enableSuppression 是否启用抑制
     */
    public AlertableException(String message, String code, Throwable cause,
                              AlertLevel level, Map<String, Object> extra,
                              Boolean enableSuppression) {
        super(message, cause,
                enableSuppression == null || enableSuppression,
                enableSuppression == null || enableSuppression);
        this.code = code;
        this.level = level == null ? AlertLevel.ERROR : level;
        this.extra = extra == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(extra));
    }

    public String getCode() {
        return code;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
