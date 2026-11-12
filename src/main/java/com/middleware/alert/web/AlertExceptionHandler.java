package com.middleware.alert.web;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.FingerprintGenerator;
import com.middleware.alert.exception.AlertableException;
import com.middleware.alert.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 异常分类与上报逻辑，从 {@link BizExceptionAspect} 中抽离以便单测。
 */
public class AlertExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertExceptionHandler.class);

    /** 同一线程内同一异常只上报一次，避免嵌套 Controller 调用重复入表 */
    private static final ThreadLocal<Set<Throwable>> REPORTED =
            new ThreadLocal<Set<Throwable>>() {
                @Override
                protected Set<Throwable> initialValue() {
                    return Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
                }
            };

    /** 切面嵌套深度：仅最外层退出时清理，保证外层仍能看到内层已上报的异常 */
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private final AlertPublisher publisher;
    private final AlertConfigProvider configProvider;

    public AlertExceptionHandler(AlertPublisher publisher, AlertConfigProvider configProvider) {
        this.publisher = publisher;
        this.configProvider = configProvider;
    }

    public void handle(Throwable throwable, HttpServletRequest request) {
        if (throwable == null || !REPORTED.get().add(throwable)) {
            return;
        }
        try {
            if (throwable instanceof AlertableException) {
                handleBiz((AlertableException) throwable, request);
            } else if (throwable instanceof StackOverflowError) {
                handleStackOverflow((StackOverflowError) throwable, request);
            } else if (throwable instanceof NullPointerException) {
                handleNpe((NullPointerException) throwable, request);
            }
        } catch (Throwable t) {
            // 采集失败绝不影响业务
            log.warn("alert exception handling failed: {}", t.toString());
        }
    }

    private void handleBiz(AlertableException exception, HttpServletRequest request) {
        if (!configProvider.get().isBizEnabled()) {
            return;
        }
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        String title = exception.getCode() == null
                ? message
                : "[" + exception.getCode() + "] " + message;

        publisher.publish(publisher.newEvent()
                .alertType(AlertType.BIZ_EXCEPTION)
                .alertLevel(exception.getLevel())
                .title(truncate(title, 512))
                .content(message)
                .exceptionType(exception.getClass().getName())
                .stackTrace(stackTrace(exception, 0))
                .requestUri(requestUri(request))
                .httpMethod(httpMethod(request))
                .fingerprint(publisher.getFingerprints().exception(AlertType.BIZ_EXCEPTION, exception))
                .extra(exception.getExtra().isEmpty() ? null : JsonUtils.toJson(exception.getExtra()))
                .build());
    }

    private void handleStackOverflow(StackOverflowError error, HttpServletRequest request) {
        if (!configProvider.get().isStackOverflowEnabled()) {
            return;
        }
        // 栈已耗尽：只取前 20 帧，避免构造大对象
        publishError(AlertType.STACK_OVERFLOW, AlertLevel.CRITICAL, error, request, 20);
    }

    private void handleNpe(NullPointerException error, HttpServletRequest request) {
        AlertRuntimeConfig config = configProvider.get();
        if (!config.isNpeEnabled() || !sampled(config.getNpeSampleRate())) {
            return;
        }
        publishError(AlertType.NULL_POINTER, AlertLevel.ERROR, error, request, 5);
    }

    private void publishError(AlertType type, AlertLevel level, Throwable throwable,
                              HttpServletRequest request, int maxFrames) {
        String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
        String title = throwable.getClass().getSimpleName() + ": " + message;

        publisher.publish(publisher.newEvent()
                .alertType(type)
                .alertLevel(level)
                .title(truncate(title, 512))
                .content(message)
                .exceptionType(throwable.getClass().getName())
                .stackTrace(stackTrace(throwable, maxFrames))
                .requestUri(requestUri(request))
                .httpMethod(httpMethod(request))
                .fingerprint(publisher.getFingerprints().exception(type, throwable))
                .build());
    }

    private String stackTrace(Throwable throwable, int maxFrames) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(": ").append(throwable.getMessage());
        }
        StackTraceElement[] elements = throwable.getStackTrace();
        int total = elements == null ? 0 : elements.length;
        int limit = maxFrames <= 0 ? total : Math.min(maxFrames, total);
        for (int i = 0; i < limit; i++) {
            sb.append("\n\tat ").append(elements[i]);
        }
        if (limit < total) {
            sb.append("\n\t... ").append(total - limit).append(" more");
        }
        return truncate(sb.toString(), configProvider.get().getStackTraceMaxLength());
    }

    private static String requestUri(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }

    private static String httpMethod(HttpServletRequest request) {
        return request == null ? null : request.getMethod();
    }

    private static boolean sampled(double rate) {
        if (rate >= 1.0D) {
            return true;
        }
        if (rate <= 0D) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 切面进入时调用。
     */
    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    /**
     * 切面退出时调用。仅最外层退出时清理 ThreadLocal，
     * 保证嵌套 Controller 调用中内层已上报的异常不会被外层重复上报。
     */
    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
            REPORTED.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    /**
     * 强制清理，供测试与异常兜底使用。
     */
    public static void clearThreadState() {
        DEPTH.remove();
        REPORTED.remove();
    }
}
