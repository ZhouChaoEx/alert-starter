package com.middleware.alert.http;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * RestTemplate 出站拦截器。
 *
 * <p>通过 {@code RestTemplateCustomizer} 自动加到容器管理的 RestTemplate 上，
 * 业务代码不需要改动。<b>只对 Spring 容器里的 RestTemplate Bean 生效</b>：
 * 业务自己 {@code new RestTemplate()} 的实例不经过 Customizer，不会被采集。</p>
 *
 * <p>不读取响应体，避免影响业务对 body 的读取。</p>
 */
public class OutboundRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final OutboundReporter reporter;
    private final AlertConfigProvider configProvider;

    public OutboundRestTemplateInterceptor(OutboundReporter reporter,
                                           AlertConfigProvider configProvider) {
        this.reporter = reporter;
        this.configProvider = configProvider;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        AlertRuntimeConfig config = configProvider.get();
        if (config == null || !config.isHttpOutboundEnabled()) {
            return execution.execute(request, body);
        }

        URI uri = request.getURI();
        String url = OutboundUrls.safe(uri);
        String host = uri == null ? null : uri.getHost();
        String path = uri == null ? null : uri.getPath();
        String method = request.getMethod() == null ? null : request.getMethod().name();

        long start = System.currentTimeMillis();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long cost = System.currentTimeMillis() - start;
            reporter.reportIfNeeded(url, host, path, method,
                    response.getRawStatusCode(), cost, null, config);
            return response;
        } catch (IOException e) {
            long cost = System.currentTimeMillis() - start;
            reporter.reportIfNeeded(url, host, path, method, -1, cost, e, config);
            throw e;
        }
    }
}
