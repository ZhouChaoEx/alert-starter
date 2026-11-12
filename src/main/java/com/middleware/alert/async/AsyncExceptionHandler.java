package com.middleware.alert.async;

import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

/**
 * 异步异常采集：注册为 {@link org.springframework.scheduling.annotation.AsyncConfigurer#getAsyncUncaughtExceptionHandler()}。
 *
 * <p>只覆盖返回值为 {@code void} 的 {@code @Async} 方法抛出的未捕获异常；
 * 自建 {@code ThreadPoolExecutor#submit} 的任务与 {@code @Scheduled} 任务不在覆盖范围内。
 * 开关每次回调时判定，页面开启后无需重启。</p>
 */
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    private final AlertPublisher publisher;
    private final AlertConfigProvider configProvider;

    public AsyncExceptionHandler(AlertPublisher publisher, AlertConfigProvider configProvider) {
        this.publisher = publisher;
        this.configProvider = configProvider;
    }

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        if (throwable == null) {
            return;
        }
        try {
            AlertRuntimeConfig config = configProvider.get();
            if (config == null || !config.isAsyncEnabled()) {
                return;
            }
            String signature = signature(method, config.isAsyncArgsTypes());
            String message = throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();

            publisher.publish(publisher.newEvent()
                    .alertType(AlertType.ASYNC_EXCEPTION)
                    .alertLevel(AlertLevel.ERROR)
                    .title(truncate("异步异常: " + signature + ": " + message, 512))
                    .content(message)
                    .exceptionType(throwable.getClass().getName())
                    .stackTrace(stackTrace(throwable, config.getStackTraceMaxLength()))
                    .fingerprint(publisher.getFingerprints()
                            .exception(AlertType.ASYNC_EXCEPTION, throwable))
                    .build());
        } catch (Throwable t) {
            // 采集失败绝不影响异步线程
            log.warn("alert async exception report failed: {}", t.toString());
        }
    }

    /**
     * 方法签名。只记录参数<b>类型</b>，不记录实参，避免把业务数据写进告警表。
     */
    static String signature(Method method, boolean withArgs) {
        if (method == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append(method.getDeclaringClass().getName()).append('#').append(method.getName());
        if (withArgs) {
            Class<?>[] types = method.getParameterTypes();
            sb.append('(');
            for (int i = 0; i < types.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(types[i].getSimpleName());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable throwable, int maxLength) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            sb.append(": ").append(throwable.getMessage());
        }
        StackTraceElement[] elements = throwable.getStackTrace();
        int total = elements == null ? 0 : elements.length;
        for (int i = 0; i < total; i++) {
            sb.append("\n\tat ").append(elements[i]);
        }
        return truncate(sb.toString(), maxLength);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
