package com.middleware.alert;

import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手动上报门面，供未覆盖的场景使用。
 *
 * <pre>
 * AlertReporter.report(AlertType.BIZ_EXCEPTION, AlertLevel.ERROR, "标题", "内容", null);
 * </pre>
 *
 * <p>所有方法均不抛出异常；未启用或未绑定发布器时只打日志。</p>
 */
public final class AlertReporter {

    private static final Logger log = LoggerFactory.getLogger(AlertReporter.class);

    private static volatile AlertPublisher publisher;
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

    private AlertReporter() {
    }

    /**
     * 由自动装配调用，不要手工调用。
     */
    public static void bind(AlertPublisher alertPublisher) {
        publisher = alertPublisher;
    }

    /**
     * 由自动装配在上下文关闭时调用。
     */
    public static void unbind() {
        publisher = null;
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void disable() {
        ENABLED.set(false);
        AlertPublisher current = publisher;
        if (current != null) {
            current.setEnabled(false);
        }
    }

    public static void enable() {
        ENABLED.set(true);
        AlertPublisher current = publisher;
        if (current != null) {
            current.setEnabled(true);
        }
    }

    /**
     * 手动上报一条告警。
     */
    public static void report(AlertType type, AlertLevel level,
                              String title, String content, Map<String, Object> extra) {
        if (!ENABLED.get()) {
            return;
        }
        AlertPublisher current = publisher;
        if (current == null) {
            log.debug("alert reporter not bound, event ignored: {}", title);
            return;
        }
        try {
            AlertEvent event = current.newEvent()
                    .alertType(type)
                    .alertLevel(level == null ? AlertLevel.ERROR : level)
                    .title(truncate(title, 512))
                    .content(content)
                    .fingerprint(current.getFingerprints().of(type, title))
                    .extra(extra == null || extra.isEmpty() ? null : JsonUtils.toJson(extra))
                    .build();
            current.publish(event);
        } catch (Throwable t) {
            log.warn("alert report failed", t);
        }
    }

    /**
     * 直接上报已构造好的事件。
     */
    public static void report(AlertEvent event) {
        if (!ENABLED.get() || event == null) {
            return;
        }
        AlertPublisher current = publisher;
        if (current == null) {
            log.debug("alert reporter not bound, event ignored: {}", event);
            return;
        }
        current.publish(event);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
