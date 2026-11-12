package com.middleware.alert.config;

import com.middleware.alert.TestSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertConfigParserTest {

    private final AlertConfigParser parser = new AlertConfigParser();

    @Test
    void parsesAllSupportedTypes() {
        AlertProperties defaults = TestSupport.properties();
        List<AlertConfigRow> rows = Arrays.asList(
                row("runtime.enabled", "false", "BOOL"),
                row("write-mode", "sync", "STRING"),
                row("slow-request.threshold-ms", "2500", "LONG"),
                row("slow-request.ignore-paths", "/a/**,/b", "LIST"),
                row("error.npe.sample-rate", "0.25", "DOUBLE"),
                row("biz.enabled", "false", "BOOL")
        );

        AlertRuntimeConfig config = parser.parse(rows, defaults);

        assertThat(config.isRuntimeEnabled()).isFalse();
        assertThat(config.getWriteMode()).isEqualTo(AlertProperties.WriteMode.SYNC);
        assertThat(config.getSlowRequestThresholdMs()).isEqualTo(2500L);
        assertThat(config.getSlowRequestIgnorePaths()).containsExactly("/a/**", "/b");
        assertThat(config.getNpeSampleRate()).isEqualTo(0.25D);
        assertThat(config.isBizEnabled()).isFalse();
        assertThat(config.getBatchSize()).isEqualTo(defaults.getBatch().getSize());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        AlertProperties defaults = TestSupport.properties();
        defaults.getSlowRequest().setThresholdMs(1234L);

        AlertRuntimeConfig config = parser.parse(Arrays.asList(
                row("slow-request.threshold-ms", "abc", "LONG"),
                row("error.npe.sample-rate", "2.0", "DOUBLE"),
                row("biz.enabled", "maybe", "BOOL")
        ), defaults);

        assertThat(config.getSlowRequestThresholdMs()).isEqualTo(1234L);
        assertThat(config.getNpeSampleRate()).isEqualTo(1.0D);
        assertThat(config.isBizEnabled()).isTrue();
    }

    @Test
    void missingKeysUseDefaults() {
        AlertProperties defaults = TestSupport.properties();
        defaults.getSlowRequest().setThresholdMs(999L);

        AlertRuntimeConfig config = parser.parse(Collections.<AlertConfigRow>emptyList(), defaults);

        assertThat(config.getSlowRequestThresholdMs()).isEqualTo(999L);
        assertThat(config.isRuntimeEnabled()).isTrue();
        assertThat(config.isBizEnabled()).isTrue();
    }

    @Test
    void emptyListValueClearsIgnorePaths() {
        AlertProperties defaults = TestSupport.properties();
        assertThat(defaults.getSlowRequest().getIgnorePaths()).isNotEmpty();

        AlertRuntimeConfig config = parser.parse(
                Collections.singletonList(row("slow-request.ignore-paths", "", "LIST")), defaults);

        assertThat(config.getSlowRequestIgnorePaths()).isEmpty();
    }

    @Test
    void unknownKeyIsIgnored() {
        AlertProperties defaults = TestSupport.properties();

        AlertRuntimeConfig config = parser.parse(
                Collections.singletonList(row("not.exists", "x", "STRING")), defaults);

        assertThat(config.getBatchSize()).isEqualTo(defaults.getBatch().getSize());
    }

    @Test
    void negativeOrZeroValuesAreRejected() {
        AlertProperties defaults = TestSupport.properties();
        defaults.getBatch().setSize(77);

        AlertRuntimeConfig config = parser.parse(Arrays.asList(
                row("batch.size", "0", "INT"),
                row("batch.interval-ms", "-5", "LONG"),
                row("rate-limit.per-minute", "-1", "INT")
        ), defaults);

        assertThat(config.getBatchSize()).isEqualTo(77);
        assertThat(config.getBatchIntervalMs()).isEqualTo(defaults.getBatch().getIntervalMs());
        assertThat(config.getRateLimitPerMinute()).isEqualTo(defaults.getRateLimit().getPerMinute());
    }

    @Test
    void parsesAsyncAndSlowSqlKeys() {
        AlertProperties defaults = TestSupport.properties();
        AlertRuntimeConfig config = parser.parse(Arrays.asList(
                row("async.enabled", "true", "BOOL"),
                row("async.args-types", "false", "BOOL"),
                row("slow-sql.enabled", "true", "BOOL"),
                row("slow-sql.threshold-ms", "800", "LONG"),
                row("slow-sql.max-sql-length", "1024", "INT"),
                row("slow-sql.exclude-mapper-prefixes", "com.a.,com.b.", "LIST"),
                row("slow-sql.aggregate-window-seconds", "0", "INT")
        ), defaults);

        assertThat(config.isAsyncEnabled()).isTrue();
        assertThat(config.isAsyncArgsTypes()).isFalse();
        assertThat(config.isSlowSqlEnabled()).isTrue();
        assertThat(config.getSlowSqlThresholdMs()).isEqualTo(800L);
        assertThat(config.getSlowSqlMaxSqlLength()).isEqualTo(1024);
        assertThat(config.getSlowSqlExcludeMapperPrefixes()).containsExactly("com.a.", "com.b.");
        assertThat(config.getSlowSqlAggregateWindowSeconds()).isZero();
    }

    @Test
    void newCollectorsAreDisabledByDefault() {
        AlertRuntimeConfig config = parser.parse(
                Collections.<AlertConfigRow>emptyList(), TestSupport.properties());

        assertThat(config.isAsyncEnabled()).isFalse();
        assertThat(config.isSlowSqlEnabled()).isFalse();
        assertThat(config.getSlowSqlThresholdMs()).isEqualTo(500L);
    }

    @Test
    void asMapExposesNewCollectorKeysForRefreshDiff() {
        AlertRuntimeConfig config = AlertRuntimeConfig.from(TestSupport.properties());

        assertThat(config.asMap()).containsKeys(
                "async.enabled", "async.args-types",
                "slow-sql.enabled", "slow-sql.threshold-ms", "slow-sql.max-sql-length",
                "slow-sql.exclude-mapper-prefixes", "slow-sql.aggregate-window-seconds",
                "memory.enabled", "memory.interval-ms", "memory.old-gen-ratio",
                "memory.consecutive-samples",
                "http.outbound.enabled", "http.outbound.threshold-ms",
                "http.outbound.slow-enabled", "http.outbound.statuses",
                "http.outbound.exclude-hosts", "http.outbound.exclude-paths");
    }

    @Test
    void parsesHttpOutboundKeys() {
        AlertRuntimeConfig config = parser.parse(Arrays.asList(
                row("http.outbound.enabled", "true", "BOOL"),
                row("http.outbound.threshold-ms", "1500", "LONG"),
                row("http.outbound.slow-enabled", "false", "BOOL"),
                row("http.outbound.statuses", "500,502,504", "LIST"),
                row("http.outbound.exclude-hosts", "*.internal.com,localhost", "LIST"),
                row("http.outbound.exclude-paths", "/actuator/**,/health", "LIST")
        ), TestSupport.properties());

        assertThat(config.isHttpOutboundEnabled()).isTrue();
        assertThat(config.getHttpOutboundThresholdMs()).isEqualTo(1500L);
        assertThat(config.isHttpOutboundSlowEnabled()).isFalse();
        assertThat(config.getHttpOutboundStatuses()).containsExactly(500, 502, 504);
        assertThat(config.getHttpOutboundExcludeHosts())
                .containsExactly("*.internal.com", "localhost");
        assertThat(config.getHttpOutboundExcludePaths())
                .containsExactly("/actuator/**", "/health");
    }

    @Test
    void invalidStatusCodeIsIgnored() {
        AlertRuntimeConfig config = parser.parse(
                Collections.singletonList(row("http.outbound.statuses", "502,abc,504", "LIST")),
                TestSupport.properties());

        assertThat(config.getHttpOutboundStatuses()).containsExactly(502, 504);
    }

    @Test
    void httpOutboundIsDisabledByDefault() {
        AlertRuntimeConfig config = parser.parse(
                Collections.<AlertConfigRow>emptyList(), TestSupport.properties());

        assertThat(config.isHttpOutboundEnabled()).isFalse();
        assertThat(config.getHttpOutboundThresholdMs()).isEqualTo(3000L);
        assertThat(config.isHttpOutboundSlowEnabled()).isTrue();
        assertThat(config.getHttpOutboundStatuses()).containsExactly(502, 503, 504);
        assertThat(config.getHttpOutboundExcludeHosts()).isEmpty();
        assertThat(config.getHttpOutboundExcludePaths()).isEmpty();
    }

    @Test
    void parsesMemoryKeys() {
        AlertRuntimeConfig config = parser.parse(Arrays.asList(
                row("memory.enabled", "true", "BOOL"),
                row("memory.interval-ms", "30000", "LONG"),
                row("memory.old-gen-ratio", "0.9", "DOUBLE"),
                row("memory.consecutive-samples", "5", "INT")
        ), TestSupport.properties());

        assertThat(config.isMemoryEnabled()).isTrue();
        assertThat(config.getMemoryIntervalMs()).isEqualTo(30000L);
        assertThat(config.getMemoryOldGenRatio()).isEqualTo(0.9D);
        assertThat(config.getMemoryConsecutiveSamples()).isEqualTo(5);
    }

    @Test
    void memoryIsDisabledByDefault() {
        AlertRuntimeConfig config = parser.parse(
                Collections.<AlertConfigRow>emptyList(), TestSupport.properties());

        assertThat(config.isMemoryEnabled()).isFalse();
        assertThat(config.getMemoryIntervalMs()).isEqualTo(60000L);
        assertThat(config.getMemoryOldGenRatio()).isEqualTo(0.85D);
        assertThat(config.getMemoryConsecutiveSamples()).isEqualTo(3);
    }

    private static AlertConfigRow row(String key, String value, String type) {
        return new AlertConfigRow(key, value, type, 1L);
    }
}
