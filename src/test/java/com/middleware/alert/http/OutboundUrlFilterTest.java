package com.middleware.alert.http;

import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundUrlFilterTest {

    private final OutboundUrlFilter filter = new OutboundUrlFilter();

    @Test
    void exactHostMatches() {
        assertThat(OutboundUrlFilter.hostMatch("api.example.com", "api.example.com")).isTrue();
        assertThat(OutboundUrlFilter.hostMatch("API.example.com", "api.example.com")).isTrue();
        assertThat(OutboundUrlFilter.hostMatch("other.example.com", "api.example.com")).isFalse();
    }

    @Test
    void wildcardSubdomainMatches() {
        assertThat(OutboundUrlFilter.hostMatch("a.internal.com", "*.internal.com")).isTrue();
        assertThat(OutboundUrlFilter.hostMatch("a.b.internal.com", "*.internal.com")).isTrue();
        assertThat(OutboundUrlFilter.hostMatch("internal.com", "*.internal.com")).isFalse();
    }

    @Test
    void starMatchesEverything() {
        assertThat(OutboundUrlFilter.hostMatch("any.host", "*")).isTrue();
    }

    @Test
    void exactPathMatches() {
        assertThat(OutboundUrlFilter.pathMatch("/health", "/health")).isTrue();
        assertThat(OutboundUrlFilter.pathMatch("/health2", "/health")).isFalse();
    }

    @Test
    void prefixPathMatches() {
        assertThat(OutboundUrlFilter.pathMatch("/actuator/env", "/actuator/**")).isTrue();
        assertThat(OutboundUrlFilter.pathMatch("/actuator", "/actuator/**")).isTrue();
        assertThat(OutboundUrlFilter.pathMatch("/api/actuator", "/actuator/**")).isFalse();
    }

    @Test
    void singleSegmentWildcardMatches() {
        assertThat(OutboundUrlFilter.pathMatch("/api/v1/status", "/api/*/status")).isTrue();
        assertThat(OutboundUrlFilter.pathMatch("/api/v1/v2/status", "/api/*/status")).isFalse();
    }

    @Test
    void excludedByHostPattern() {
        AlertRuntimeConfig config = config(Arrays.asList("*.internal.com"), null);

        assertThat(filter.excluded("a.internal.com", "/api/x", config)).isTrue();
        assertThat(filter.excluded("external.com", "/api/x", config)).isFalse();
    }

    @Test
    void excludedByPathPattern() {
        AlertRuntimeConfig config = config(null, Arrays.asList("/actuator/**"));

        assertThat(filter.excluded("any", "/actuator/health", config)).isTrue();
        assertThat(filter.excluded("any", "/api/x", config)).isFalse();
    }

    @Test
    void nullConfigExcludesEverything() {
        assertThat(filter.excluded("any", "/api", null)).isTrue();
    }

    private static AlertRuntimeConfig config(java.util.List<String> hosts,
                                             java.util.List<String> paths) {
        AlertProperties properties = TestSupport.properties();
        if (hosts != null) {
            properties.getHttp().getOutbound().setExcludeHosts(hosts);
        }
        if (paths != null) {
            properties.getHttp().getOutbound().setExcludePaths(paths);
        }
        return AlertRuntimeConfig.from(properties);
    }
}
