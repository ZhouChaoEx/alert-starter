package com.middleware.alert.web;

import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 慢请求采集过滤器。
 *
 * <p>只统计耗时，不采集入站状态码。同步请求在 filter 返回时结算；
 * 异步请求（Callable/DeferredResult）在 {@link AsyncListener#onComplete} 时结算。</p>
 */
public class SlowRequestFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(SlowRequestFilter.class);

    private final AlertPublisher publisher;
    private final AlertConfigProvider configProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SlowRequestFilter(AlertPublisher publisher, AlertConfigProvider configProvider) {
        this.publisher = publisher;
        this.configProvider = configProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AlertRuntimeConfig config = configProvider.get();
        if (!config.isSlowRequestEnabled() || isIgnored(request, config)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                registerAsyncListener(request, startNanos);
            } else {
                publishIfSlow(request, response.getStatus(), startNanos);
            }
        }
    }

    private void registerAsyncListener(final HttpServletRequest request, final long startNanos) {
        try {
            AsyncContext asyncContext = request.getAsyncContext();
            asyncContext.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    publishIfSlow(request, statusOf(event), startNanos);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    publishIfSlow(request, statusOf(event), startNanos);
                }

                @Override
                public void onError(AsyncEvent event) {
                    publishIfSlow(request, statusOf(event), startNanos);
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    // 嵌套异步，沿用原起始时间
                }
            });
        } catch (IllegalStateException e) {
            // 异步周期已结束，按当前耗时兜底结算
            publishIfSlow(request, null, startNanos);
        }
    }

    private static Integer statusOf(AsyncEvent event) {
        if (event.getSuppliedResponse() instanceof HttpServletResponse) {
            return ((HttpServletResponse) event.getSuppliedResponse()).getStatus();
        }
        return null;
    }

    private void publishIfSlow(HttpServletRequest request, Integer status, long startNanos) {
        try {
            AlertRuntimeConfig config = configProvider.get();
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long thresholdMs = config.getSlowRequestThresholdMs();
            if (costMs < thresholdMs) {
                return;
            }
            String uri = requestUri(request);
            String method = request.getMethod();
            publisher.publish(publisher.newEvent()
                    .alertType(AlertType.SLOW_REQUEST)
                    .alertLevel(AlertLevel.WARN)
                    .title(truncate("慢请求: " + method + " " + uri + " " + costMs + "ms", 512))
                    .content("请求耗时 " + costMs + "ms，超过阈值 " + thresholdMs + "ms")
                    .requestUri(truncate(uri, 512))
                    .httpMethod(method)
                    .httpStatus(status)
                    .costMs(costMs)
                    .fingerprint(publisher.getFingerprints().of(AlertType.SLOW_REQUEST, uri))
                    .build());
        } catch (Throwable t) {
            log.warn("slow request alert failed: {}", t.toString());
        }
    }

    private boolean isIgnored(HttpServletRequest request, AlertRuntimeConfig config) {
        String uri = request.getRequestURI();
        for (String pattern : config.getSlowRequestIgnorePaths()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private static String requestUri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
