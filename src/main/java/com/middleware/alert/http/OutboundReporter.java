package com.middleware.alert.http;

import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 出站 HTTP 判定与上报，供 RestTemplate 拦截器与 Feign 代理共用。
 *
 * <p>命中任一条件即上报 {@link AlertType#HTTP_OUTBOUND}：</p>
 * <ol>
 *   <li>下游状态码命中 {@code http.outbound.statuses}（默认 502/503/504）；</li>
 *   <li>调用抛异常（连接失败、读超时等）；</li>
 *   <li>{@code slow-enabled=true} 且耗时 >= {@code threshold-ms}（默认 3000ms）。</li>
 * </ol>
 *
 * <p>只记录 URL（已去 query）、方法、状态码、耗时与异常类型，<b>不采集请求体/响应体</b>。</p>
 */
public class OutboundReporter {

    private static final Logger log = LoggerFactory.getLogger(OutboundReporter.class);

    private final AlertPublisher publisher;
    private final OutboundUrlFilter filter;

    public OutboundReporter(AlertPublisher publisher) {
        this(publisher, new OutboundUrlFilter());
    }

    public OutboundReporter(AlertPublisher publisher, OutboundUrlFilter filter) {
        this.publisher = publisher;
        this.filter = filter;
    }

    /**
     * 判定并上报。
     *
     * @param url     target_url（scheme+host+path，已剔除 query）
     * @param host    主机，用于 exclude-hosts 匹配
     * @param path    路径，用于 exclude-paths 匹配
     * @param method  HTTP 方法
     * @param status  下游状态码；{@code <=0} 表示没有拿到响应（连接失败/超时）
     * @param costMs  耗时（毫秒）
     * @param error   调用异常，可为 null
     * @param config  运行时快照
     */
    public void reportIfNeeded(String url, String host, String path, String method,
                               int status, long costMs, Throwable error,
                               AlertRuntimeConfig config) {
        if (config == null || !config.isHttpOutboundEnabled()) {
            return;
        }
        if (filter.excluded(host, path, config)) {
            return;
        }

        boolean statusHit = status > 0 && config.getHttpOutboundStatuses().contains(status);
        boolean errorHit = error != null;
        boolean slowHit = config.isHttpOutboundSlowEnabled()
                && costMs >= config.getHttpOutboundThresholdMs();
        if (!statusHit && !errorHit && !slowHit) {
            return;
        }

        try {
            AlertLevel level = (statusHit || errorHit) ? AlertLevel.ERROR : AlertLevel.WARN;
            String title = buildTitle(statusHit, errorHit, slowHit, url, method, status, costMs, error);
            String content = buildContent(statusHit, errorHit, slowHit, url, method, status,
                    costMs, error, config);

            publisher.publish(publisher.newEvent()
                    .alertType(AlertType.HTTP_OUTBOUND)
                    .alertLevel(level)
                    .title(truncate(title, 512))
                    .content(content)
                    .targetUrl(truncate(url, 512))
                    .httpMethod(method)
                    .httpStatus(status > 0 ? Integer.valueOf(status) : null)
                    .costMs(costMs)
                    .exceptionType(error == null ? null : error.getClass().getName())
                    .fingerprint(publisher.getFingerprints()
                            .of(AlertType.HTTP_OUTBOUND, (url == null ? "" : url) + "|" + status))
                    .build());
        } catch (Throwable t) {
            // 采集失败绝不影响业务调用
            log.warn("alert outbound http report failed: {}", t.toString());
        }
    }

    private static String buildTitle(boolean statusHit, boolean errorHit, boolean slowHit,
                                     String url, String method, int status, long costMs,
                                     Throwable error) {
        String prefix;
        if (errorHit) {
            prefix = "出站调用异常";
        } else if (statusHit) {
            prefix = "出站 HTTP " + status;
        } else {
            prefix = "出站慢调用";
        }
        StringBuilder sb = new StringBuilder(128).append(prefix).append(": ");
        if (method != null && !method.isEmpty()) {
            sb.append(method).append(' ');
        }
        sb.append(url == null ? "-" : url).append(' ').append(costMs).append("ms");
        if (errorHit) {
            sb.append(" (").append(error.getClass().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    private static String buildContent(boolean statusHit, boolean errorHit, boolean slowHit,
                                       String url, String method, int status, long costMs,
                                       Throwable error, AlertRuntimeConfig config) {
        StringBuilder sb = new StringBuilder(160);
        sb.append(method == null ? "出站调用" : method).append(' ')
                .append(url == null ? "-" : url).append('\n');
        sb.append("耗时: ").append(costMs).append("ms（阈值 ")
                .append(config.getHttpOutboundThresholdMs()).append("ms）\n");
        sb.append("状态码: ").append(status > 0 ? String.valueOf(status) : "无响应").append('\n');
        if (statusHit) {
            sb.append("命中异常状态码列表: ").append(config.getHttpOutboundStatuses()).append('\n');
        }
        if (errorHit) {
            sb.append("异常: ").append(error.getClass().getName());
            if (error.getMessage() != null) {
                sb.append(": ").append(error.getMessage());
            }
            sb.append('\n');
        }
        if (slowHit && !errorHit && !statusHit) {
            sb.append("调用成功但超过慢阈值\n");
        }
        return sb.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
