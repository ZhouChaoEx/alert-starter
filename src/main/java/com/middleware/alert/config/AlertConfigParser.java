package com.middleware.alert.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把 alert_config 表行解析成 {@link AlertRuntimeConfig}。
 *
 * <p>单条配置非法时记录 WARN 并保留默认值/上一条有效值，不整体失败。</p>
 */
public class AlertConfigParser {

    private static final Logger log = LoggerFactory.getLogger(AlertConfigParser.class);

    public AlertRuntimeConfig parse(List<AlertConfigRow> rows, AlertProperties defaults) {
        AlertRuntimeConfig.Builder builder = AlertRuntimeConfig.builder(defaults);
        if (rows == null || rows.isEmpty()) {
            return builder.build();
        }
        for (AlertConfigRow row : rows) {
            if (row == null || row.getConfigKey() == null) {
                continue;
            }
            try {
                apply(builder, row);
            } catch (Exception e) {
                log.warn("alert config invalid, key={}, value={}, type={}: {}",
                        row.getConfigKey(), row.getConfigValue(), row.getValueType(), e.getMessage());
            }
        }
        return builder.build();
    }

    private void apply(AlertRuntimeConfig.Builder builder, AlertConfigRow row) {
        String key = row.getConfigKey().trim();
        String value = row.getConfigValue();
        switch (key) {
            case "runtime.enabled":
                builder.runtimeEnabled(parseBool(value));
                break;
            case "write-mode":
                builder.writeMode(parseWriteMode(value));
                break;
            case "stack-trace.max-length":
                builder.stackTraceMaxLength(positiveInt(value));
                break;
            case "batch.size":
                builder.batchSize(positiveInt(value));
                break;
            case "batch.interval-ms":
                builder.batchIntervalMs(positiveLong(value));
                break;
            case "dedup.window-seconds":
                builder.dedupWindowSeconds(positiveInt(value));
                break;
            case "dedup.max-entries":
                builder.dedupMaxEntries(positiveInt(value));
                break;
            case "rate-limit.per-minute":
                builder.rateLimitPerMinute(nonNegativeInt(value));
                break;
            case "rate-limit.sample-factor":
                builder.rateLimitSampleFactor(positiveInt(value));
                break;
            case "biz.enabled":
                builder.bizEnabled(parseBool(value));
                break;
            case "error.npe.enabled":
                builder.npeEnabled(parseBool(value));
                break;
            case "error.npe.sample-rate":
                builder.npeSampleRate(sampleRate(value));
                break;
            case "error.stackoverflow.enabled":
                builder.stackOverflowEnabled(parseBool(value));
                break;
            case "slow-request.enabled":
                builder.slowRequestEnabled(parseBool(value));
                break;
            case "slow-request.threshold-ms":
                builder.slowRequestThresholdMs(positiveLong(value));
                break;
            case "slow-request.ignore-paths":
                builder.slowRequestIgnorePaths(parseList(value));
                break;
            case "async.enabled":
                builder.asyncEnabled(parseBool(value));
                break;
            case "async.args-types":
                builder.asyncArgsTypes(parseBool(value));
                break;
            case "slow-sql.enabled":
                builder.slowSqlEnabled(parseBool(value));
                break;
            case "slow-sql.threshold-ms":
                builder.slowSqlThresholdMs(positiveLong(value));
                break;
            case "slow-sql.max-sql-length":
                builder.slowSqlMaxSqlLength(positiveInt(value));
                break;
            case "slow-sql.exclude-mapper-prefixes":
                builder.slowSqlExcludeMapperPrefixes(parseList(value));
                break;
            case "slow-sql.aggregate-window-seconds":
                builder.slowSqlAggregateWindowSeconds(nonNegativeInt(value));
                break;
            case "memory.enabled":
                builder.memoryEnabled(parseBool(value));
                break;
            case "memory.interval-ms":
                builder.memoryIntervalMs(positiveLong(value));
                break;
            case "memory.old-gen-ratio":
                builder.memoryOldGenRatio(sampleRate(value));
                break;
            case "memory.consecutive-samples":
                builder.memoryConsecutiveSamples(positiveInt(value));
                break;
            case "http.outbound.enabled":
                builder.httpOutboundEnabled(parseBool(value));
                break;
            case "http.outbound.threshold-ms":
                builder.httpOutboundThresholdMs(positiveLong(value));
                break;
            case "http.outbound.slow-enabled":
                builder.httpOutboundSlowEnabled(parseBool(value));
                break;
            case "http.outbound.statuses":
                builder.httpOutboundStatuses(parseIntList(value));
                break;
            case "http.outbound.exclude-hosts":
                builder.httpOutboundExcludeHosts(parseList(value));
                break;
            case "http.outbound.exclude-paths":
                builder.httpOutboundExcludePaths(parseList(value));
                break;
            case "queue.capacity":
            case "console.instances":
                // 队列容量在启动时创建，刷新不生效；console.instances 由管理端使用
                log.debug("alert config {}={} is not part of runtime snapshot", key, value);
                break;
            default:
                log.debug("unknown alert config key: {}", key);
        }
    }

    static boolean parseBool(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        String text = value.trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean: " + value);
    }

    static AlertProperties.WriteMode parseWriteMode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        String text = value.trim().toUpperCase();
        if (!"ASYNC".equals(text) && !"SYNC".equals(text)) {
            throw new IllegalArgumentException("write-mode must be ASYNC or SYNC: " + value);
        }
        return AlertProperties.WriteMode.valueOf(text);
    }

    static int positiveInt(String value) {
        int parsed = nonNegativeInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("must be > 0: " + value);
        }
        return parsed;
    }

    static int nonNegativeInt(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException("must be >= 0: " + value);
        }
        return parsed;
    }

    static long positiveLong(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        long parsed = Long.parseLong(value.trim());
        if (parsed <= 0L) {
            throw new IllegalArgumentException("must be > 0: " + value);
        }
        return parsed;
    }

    static double sampleRate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        double parsed = Double.parseDouble(value.trim());
        if (parsed < 0D || parsed > 1D) {
            throw new IllegalArgumentException("sample rate must be in [0,1]: " + value);
        }
        return parsed;
    }

    static List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 解析逗号分隔的整数列表（如 {@code 502,503,504}）。
     *
     * <p>非法项跳过并打 WARN，不因单个坏值让整个列表失效；全部非法时返回空列表。</p>
     */
    static List<Integer> parseIntList(String value) {
        List<String> items = parseList(value);
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<Integer>(items.size());
        for (String item : items) {
            try {
                result.add(Integer.parseInt(item));
            } catch (NumberFormatException e) {
                log.warn("alert config integer not valid, ignored: {}", item);
            }
        }
        return result;
    }
}
