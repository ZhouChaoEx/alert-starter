package com.middleware.alert.http;

import java.net.URI;

/**
 * 出站 URL 归一化：只保留 {@code scheme://host[:port]/path}。
 *
 * <p>刻意剔除 query、fragment 与 userInfo：告警表不应出现业务参数、令牌等敏感信息。</p>
 */
public final class OutboundUrls {

    private OutboundUrls() {
    }

    public static String safe(URI uri) {
        if (uri == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(96);
        String scheme = uri.getScheme();
        builder.append(scheme == null || scheme.isEmpty() ? "http" : scheme).append("://");
        String host = uri.getHost();
        builder.append(host == null ? "" : host);
        if (uri.getPort() > 0) {
            builder.append(':').append(uri.getPort());
        }
        String path = uri.getPath();
        builder.append(path == null ? "" : path);
        return builder.toString();
    }
}
