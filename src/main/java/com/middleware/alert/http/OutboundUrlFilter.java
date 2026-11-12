package com.middleware.alert.http;

import com.middleware.alert.config.AlertRuntimeConfig;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 出站 URL 过滤：按主机通配与路径 Ant 模式排除不关心的调用。
 *
 * <p>支持的形式：</p>
 * <ul>
 *   <li>主机：{@code api.example.com}（精确）、{@code *.internal.com}（后缀通配）、{@code *}（全部）</li>
 *   <li>路径：{@code /actuator/**}（前缀）、{@code /health}（精确）、{@code /api/*&#47;status}（单段通配）</li>
 * </ul>
 *
 * <p>匹配不区分大小写，便于排除域名。</p>
 */
public class OutboundUrlFilter {

    /**
     * 是否排除本次调用。
     */
    public boolean excluded(String host, String path, AlertRuntimeConfig config) {
        if (config == null) {
            return true;
        }
        List<String> hostPatterns = config.getHttpOutboundExcludeHosts();
        if (host != null && hostPatterns != null) {
            for (String pattern : hostPatterns) {
                if (hostMatch(host, pattern)) {
                    return true;
                }
            }
        }
        List<String> pathPatterns = config.getHttpOutboundExcludePaths();
        if (path != null && pathPatterns != null) {
            for (String pattern : pathPatterns) {
                if (pathMatch(path, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hostMatch(String host, String pattern) {
        if (host == null || pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        if ("*".equals(p)) {
            return true;
        }
        if (p.startsWith("*.")) {
            // *.internal.com 匹配 a.internal.com，不匹配 internal.com 本身
            return h.endsWith(p.substring(1));
        }
        return p.equals(h);
    }

    static boolean pathMatch(String path, String pattern) {
        if (path == null || pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        String p = pattern.trim();
        if (p.endsWith("/**")) {
            String prefix = p.substring(0, p.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        if (!p.contains("*")) {
            return path.equals(p);
        }
        // 仅支持单段通配 *（不跨 /），避免引入完整 Ant 实现
        StringBuilder regex = new StringBuilder();
        for (String part : p.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append("[^/]*");
            }
            regex.append(Pattern.quote(part));
        }
        return path.matches(regex.toString());
    }
}
