package com.middleware.alert.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;

/**
 * 只提供异步未捕获异常处理器，不覆盖业务方的执行器。
 *
 * <p>{@link AsyncConfigurerSupport#getAsyncExecutor()} 返回 null，
 * 与 Spring 默认行为一致：仍使用容器中的 {@code TaskExecutor}，
 * 因此引入本类不会改变业务线程池。</p>
 */
public class AlertAsyncConfigurer extends AsyncConfigurerSupport {

    private final AsyncUncaughtExceptionHandler exceptionHandler;

    public AlertAsyncConfigurer(AsyncUncaughtExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}
