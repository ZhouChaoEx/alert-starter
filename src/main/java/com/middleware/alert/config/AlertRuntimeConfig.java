package com.middleware.alert.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时配置的不可变快照。
 *
 * <p>采集点每次只读当前快照，不访问数据库；刷新时整体替换。</p>
 */
public final class AlertRuntimeConfig {

    private final boolean runtimeEnabled;
    private final AlertProperties.WriteMode writeMode;
    private final int stackTraceMaxLength;
    private final int batchSize;
    private final long batchIntervalMs;
    private final int dedupWindowSeconds;
    private final int dedupMaxEntries;
    private final int rateLimitPerMinute;
    private final int rateLimitSampleFactor;
    private final boolean bizEnabled;
    private final boolean npeEnabled;
    private final double npeSampleRate;
    private final boolean stackOverflowEnabled;
    private final boolean slowRequestEnabled;
    private final long slowRequestThresholdMs;
    private final List<String> slowRequestIgnorePaths;
    private final boolean asyncEnabled;
    private final boolean asyncArgsTypes;
    private final boolean slowSqlEnabled;
    private final long slowSqlThresholdMs;
    private final int slowSqlMaxSqlLength;
    private final List<String> slowSqlExcludeMapperPrefixes;
    private final int slowSqlAggregateWindowSeconds;
    private final boolean memoryEnabled;
    private final long memoryIntervalMs;
    private final double memoryOldGenRatio;
    private final int memoryConsecutiveSamples;
    private final boolean httpOutboundEnabled;
    private final long httpOutboundThresholdMs;
    private final boolean httpOutboundSlowEnabled;
    private final List<Integer> httpOutboundStatuses;
    private final List<String> httpOutboundExcludeHosts;
    private final List<String> httpOutboundExcludePaths;

    private AlertRuntimeConfig(Builder builder) {
        this.runtimeEnabled = builder.runtimeEnabled;
        this.writeMode = builder.writeMode;
        this.stackTraceMaxLength = builder.stackTraceMaxLength;
        this.batchSize = builder.batchSize;
        this.batchIntervalMs = builder.batchIntervalMs;
        this.dedupWindowSeconds = builder.dedupWindowSeconds;
        this.dedupMaxEntries = builder.dedupMaxEntries;
        this.rateLimitPerMinute = builder.rateLimitPerMinute;
        this.rateLimitSampleFactor = builder.rateLimitSampleFactor;
        this.bizEnabled = builder.bizEnabled;
        this.npeEnabled = builder.npeEnabled;
        this.npeSampleRate = builder.npeSampleRate;
        this.stackOverflowEnabled = builder.stackOverflowEnabled;
        this.slowRequestEnabled = builder.slowRequestEnabled;
        this.slowRequestThresholdMs = builder.slowRequestThresholdMs;
        this.slowRequestIgnorePaths = Collections.unmodifiableList(
                new ArrayList<String>(builder.slowRequestIgnorePaths));
        this.asyncEnabled = builder.asyncEnabled;
        this.asyncArgsTypes = builder.asyncArgsTypes;
        this.slowSqlEnabled = builder.slowSqlEnabled;
        this.slowSqlThresholdMs = builder.slowSqlThresholdMs;
        this.slowSqlMaxSqlLength = builder.slowSqlMaxSqlLength;
        this.slowSqlExcludeMapperPrefixes = Collections.unmodifiableList(
                new ArrayList<String>(builder.slowSqlExcludeMapperPrefixes));
        this.slowSqlAggregateWindowSeconds = builder.slowSqlAggregateWindowSeconds;
        this.memoryEnabled = builder.memoryEnabled;
        this.memoryIntervalMs = builder.memoryIntervalMs;
        this.memoryOldGenRatio = builder.memoryOldGenRatio;
        this.memoryConsecutiveSamples = builder.memoryConsecutiveSamples;
        this.httpOutboundEnabled = builder.httpOutboundEnabled;
        this.httpOutboundThresholdMs = builder.httpOutboundThresholdMs;
        this.httpOutboundSlowEnabled = builder.httpOutboundSlowEnabled;
        this.httpOutboundStatuses = Collections.unmodifiableList(
                new ArrayList<Integer>(builder.httpOutboundStatuses));
        this.httpOutboundExcludeHosts = Collections.unmodifiableList(
                new ArrayList<String>(builder.httpOutboundExcludeHosts));
        this.httpOutboundExcludePaths = Collections.unmodifiableList(
                new ArrayList<String>(builder.httpOutboundExcludePaths));
    }

    public static AlertRuntimeConfig from(AlertProperties properties) {
        return builder(properties).build();
    }

    public static Builder builder(AlertProperties properties) {
        return new Builder(properties);
    }

    public boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    public AlertProperties.WriteMode getWriteMode() {
        return writeMode;
    }

    public int getStackTraceMaxLength() {
        return stackTraceMaxLength;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getBatchIntervalMs() {
        return batchIntervalMs;
    }

    public int getDedupWindowSeconds() {
        return dedupWindowSeconds;
    }

    public int getDedupMaxEntries() {
        return dedupMaxEntries;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public int getRateLimitSampleFactor() {
        return rateLimitSampleFactor;
    }

    public boolean isBizEnabled() {
        return bizEnabled;
    }

    public boolean isNpeEnabled() {
        return npeEnabled;
    }

    public double getNpeSampleRate() {
        return npeSampleRate;
    }

    public boolean isStackOverflowEnabled() {
        return stackOverflowEnabled;
    }

    public boolean isSlowRequestEnabled() {
        return slowRequestEnabled;
    }

    public long getSlowRequestThresholdMs() {
        return slowRequestThresholdMs;
    }

    public List<String> getSlowRequestIgnorePaths() {
        return slowRequestIgnorePaths;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public boolean isAsyncArgsTypes() {
        return asyncArgsTypes;
    }

    public boolean isSlowSqlEnabled() {
        return slowSqlEnabled;
    }

    public long getSlowSqlThresholdMs() {
        return slowSqlThresholdMs;
    }

    public int getSlowSqlMaxSqlLength() {
        return slowSqlMaxSqlLength;
    }

    public List<String> getSlowSqlExcludeMapperPrefixes() {
        return slowSqlExcludeMapperPrefixes;
    }

    public int getSlowSqlAggregateWindowSeconds() {
        return slowSqlAggregateWindowSeconds;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public long getMemoryIntervalMs() {
        return memoryIntervalMs;
    }

    public double getMemoryOldGenRatio() {
        return memoryOldGenRatio;
    }

    public int getMemoryConsecutiveSamples() {
        return memoryConsecutiveSamples;
    }

    public boolean isHttpOutboundEnabled() {
        return httpOutboundEnabled;
    }

    public long getHttpOutboundThresholdMs() {
        return httpOutboundThresholdMs;
    }

    public boolean isHttpOutboundSlowEnabled() {
        return httpOutboundSlowEnabled;
    }

    public List<Integer> getHttpOutboundStatuses() {
        return httpOutboundStatuses;
    }

    public List<String> getHttpOutboundExcludeHosts() {
        return httpOutboundExcludeHosts;
    }

    public List<String> getHttpOutboundExcludePaths() {
        return httpOutboundExcludePaths;
    }

    /**
     * 用于刷新时对比变更项，key 与 alert_config.config_key 一致。
     */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("runtime.enabled", runtimeEnabled);
        map.put("write-mode", writeMode == null ? null : writeMode.name());
        map.put("stack-trace.max-length", stackTraceMaxLength);
        map.put("batch.size", batchSize);
        map.put("batch.interval-ms", batchIntervalMs);
        map.put("dedup.window-seconds", dedupWindowSeconds);
        map.put("dedup.max-entries", dedupMaxEntries);
        map.put("rate-limit.per-minute", rateLimitPerMinute);
        map.put("rate-limit.sample-factor", rateLimitSampleFactor);
        map.put("biz.enabled", bizEnabled);
        map.put("error.npe.enabled", npeEnabled);
        map.put("error.npe.sample-rate", npeSampleRate);
        map.put("error.stackoverflow.enabled", stackOverflowEnabled);
        map.put("slow-request.enabled", slowRequestEnabled);
        map.put("slow-request.threshold-ms", slowRequestThresholdMs);
        map.put("slow-request.ignore-paths", slowRequestIgnorePaths);
        map.put("async.enabled", asyncEnabled);
        map.put("async.args-types", asyncArgsTypes);
        map.put("slow-sql.enabled", slowSqlEnabled);
        map.put("slow-sql.threshold-ms", slowSqlThresholdMs);
        map.put("slow-sql.max-sql-length", slowSqlMaxSqlLength);
        map.put("slow-sql.exclude-mapper-prefixes", slowSqlExcludeMapperPrefixes);
        map.put("slow-sql.aggregate-window-seconds", slowSqlAggregateWindowSeconds);
        map.put("memory.enabled", memoryEnabled);
        map.put("memory.interval-ms", memoryIntervalMs);
        map.put("memory.old-gen-ratio", memoryOldGenRatio);
        map.put("memory.consecutive-samples", memoryConsecutiveSamples);
        map.put("http.outbound.enabled", httpOutboundEnabled);
        map.put("http.outbound.threshold-ms", httpOutboundThresholdMs);
        map.put("http.outbound.slow-enabled", httpOutboundSlowEnabled);
        map.put("http.outbound.statuses", httpOutboundStatuses);
        map.put("http.outbound.exclude-hosts", httpOutboundExcludeHosts);
        map.put("http.outbound.exclude-paths", httpOutboundExcludePaths);
        return map;
    }

    public static final class Builder {

        private boolean runtimeEnabled = true;
        private AlertProperties.WriteMode writeMode;
        private int stackTraceMaxLength;
        private int batchSize;
        private long batchIntervalMs;
        private int dedupWindowSeconds;
        private int dedupMaxEntries;
        private int rateLimitPerMinute;
        private int rateLimitSampleFactor;
        private boolean bizEnabled;
        private boolean npeEnabled;
        private double npeSampleRate;
        private boolean stackOverflowEnabled;
        private boolean slowRequestEnabled;
        private long slowRequestThresholdMs;
        private List<String> slowRequestIgnorePaths = new ArrayList<String>();
        private boolean asyncEnabled;
        private boolean asyncArgsTypes;
        private boolean slowSqlEnabled;
        private long slowSqlThresholdMs;
        private int slowSqlMaxSqlLength;
        private List<String> slowSqlExcludeMapperPrefixes = new ArrayList<String>();
        private int slowSqlAggregateWindowSeconds;
        private boolean memoryEnabled;
        private long memoryIntervalMs;
        private double memoryOldGenRatio;
        private int memoryConsecutiveSamples;
        private boolean httpOutboundEnabled;
        private long httpOutboundThresholdMs;
        private boolean httpOutboundSlowEnabled;
        private List<Integer> httpOutboundStatuses = new ArrayList<Integer>();
        private List<String> httpOutboundExcludeHosts = new ArrayList<String>();
        private List<String> httpOutboundExcludePaths = new ArrayList<String>();

        private Builder(AlertProperties properties) {
            this.writeMode = properties.getWriteMode();
            this.stackTraceMaxLength = properties.getStackTrace().getMaxLength();
            this.batchSize = properties.getBatch().getSize();
            this.batchIntervalMs = properties.getBatch().getIntervalMs();
            this.dedupWindowSeconds = properties.getDedup().getWindowSeconds();
            this.dedupMaxEntries = properties.getDedup().getMaxEntries();
            this.rateLimitPerMinute = properties.getRateLimit().getPerMinute();
            this.rateLimitSampleFactor = Math.max(1, properties.getRateLimit().getSampleFactor());
            this.bizEnabled = properties.getBiz().isEnabled();
            this.npeEnabled = properties.getError().getNpe().isEnabled();
            this.npeSampleRate = properties.getError().getNpe().getSampleRate();
            this.stackOverflowEnabled = properties.getError().getStackOverflow().isEnabled();
            this.slowRequestEnabled = properties.getSlowRequest().isEnabled();
            this.slowRequestThresholdMs = properties.getSlowRequest().getThresholdMs();
            this.slowRequestIgnorePaths = new ArrayList<String>(
                    properties.getSlowRequest().getIgnorePaths());
            this.asyncEnabled = properties.getAsync().isEnabled();
            this.asyncArgsTypes = properties.getAsync().isArgsTypes();
            this.slowSqlEnabled = properties.getSlowSql().isEnabled();
            this.slowSqlThresholdMs = properties.getSlowSql().getThresholdMs();
            this.slowSqlMaxSqlLength = properties.getSlowSql().getMaxSqlLength();
            this.slowSqlExcludeMapperPrefixes = new ArrayList<String>(
                    properties.getSlowSql().getExcludeMapperPrefixes());
            this.slowSqlAggregateWindowSeconds = properties.getSlowSql().getAggregateWindowSeconds();
            this.memoryEnabled = properties.getMemory().isEnabled();
            this.memoryIntervalMs = properties.getMemory().getIntervalMs();
            this.memoryOldGenRatio = properties.getMemory().getOldGenRatio();
            this.memoryConsecutiveSamples = properties.getMemory().getConsecutiveSamples();
            this.httpOutboundEnabled = properties.getHttp().getOutbound().isEnabled();
            this.httpOutboundThresholdMs = properties.getHttp().getOutbound().getThresholdMs();
            this.httpOutboundSlowEnabled = properties.getHttp().getOutbound().isSlowEnabled();
            this.httpOutboundStatuses = new ArrayList<Integer>(
                    properties.getHttp().getOutbound().getStatuses());
            this.httpOutboundExcludeHosts = new ArrayList<String>(
                    properties.getHttp().getOutbound().getExcludeHosts());
            this.httpOutboundExcludePaths = new ArrayList<String>(
                    properties.getHttp().getOutbound().getExcludePaths());
        }

        public Builder runtimeEnabled(boolean value) {
            this.runtimeEnabled = value;
            return this;
        }

        public Builder writeMode(AlertProperties.WriteMode value) {
            this.writeMode = value;
            return this;
        }

        public Builder stackTraceMaxLength(int value) {
            this.stackTraceMaxLength = value;
            return this;
        }

        public Builder batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        public Builder batchIntervalMs(long value) {
            this.batchIntervalMs = value;
            return this;
        }

        public Builder dedupWindowSeconds(int value) {
            this.dedupWindowSeconds = value;
            return this;
        }

        public Builder dedupMaxEntries(int value) {
            this.dedupMaxEntries = value;
            return this;
        }

        public Builder rateLimitPerMinute(int value) {
            this.rateLimitPerMinute = value;
            return this;
        }

        public Builder rateLimitSampleFactor(int value) {
            this.rateLimitSampleFactor = value;
            return this;
        }

        public Builder bizEnabled(boolean value) {
            this.bizEnabled = value;
            return this;
        }

        public Builder npeEnabled(boolean value) {
            this.npeEnabled = value;
            return this;
        }

        public Builder npeSampleRate(double value) {
            this.npeSampleRate = value;
            return this;
        }

        public Builder stackOverflowEnabled(boolean value) {
            this.stackOverflowEnabled = value;
            return this;
        }

        public Builder slowRequestEnabled(boolean value) {
            this.slowRequestEnabled = value;
            return this;
        }

        public Builder slowRequestThresholdMs(long value) {
            this.slowRequestThresholdMs = value;
            return this;
        }

        public Builder slowRequestIgnorePaths(List<String> value) {
            this.slowRequestIgnorePaths = value == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(value);
            return this;
        }

        public Builder asyncEnabled(boolean value) {
            this.asyncEnabled = value;
            return this;
        }

        public Builder asyncArgsTypes(boolean value) {
            this.asyncArgsTypes = value;
            return this;
        }

        public Builder slowSqlEnabled(boolean value) {
            this.slowSqlEnabled = value;
            return this;
        }

        public Builder slowSqlThresholdMs(long value) {
            this.slowSqlThresholdMs = value;
            return this;
        }

        public Builder slowSqlMaxSqlLength(int value) {
            this.slowSqlMaxSqlLength = value;
            return this;
        }

        public Builder slowSqlExcludeMapperPrefixes(List<String> value) {
            this.slowSqlExcludeMapperPrefixes = value == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(value);
            return this;
        }

        public Builder slowSqlAggregateWindowSeconds(int value) {
            this.slowSqlAggregateWindowSeconds = value;
            return this;
        }

        public Builder memoryEnabled(boolean value) {
            this.memoryEnabled = value;
            return this;
        }

        public Builder memoryIntervalMs(long value) {
            this.memoryIntervalMs = value;
            return this;
        }

        public Builder memoryOldGenRatio(double value) {
            this.memoryOldGenRatio = value;
            return this;
        }

        public Builder memoryConsecutiveSamples(int value) {
            this.memoryConsecutiveSamples = value;
            return this;
        }

        public Builder httpOutboundEnabled(boolean value) {
            this.httpOutboundEnabled = value;
            return this;
        }

        public Builder httpOutboundThresholdMs(long value) {
            this.httpOutboundThresholdMs = value;
            return this;
        }

        public Builder httpOutboundSlowEnabled(boolean value) {
            this.httpOutboundSlowEnabled = value;
            return this;
        }

        public Builder httpOutboundStatuses(List<Integer> value) {
            this.httpOutboundStatuses = value == null
                    ? new ArrayList<Integer>()
                    : new ArrayList<Integer>(value);
            return this;
        }

        public Builder httpOutboundExcludeHosts(List<String> value) {
            this.httpOutboundExcludeHosts = value == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(value);
            return this;
        }

        public Builder httpOutboundExcludePaths(List<String> value) {
            this.httpOutboundExcludePaths = value == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(value);
            return this;
        }

        public AlertRuntimeConfig build() {
            return new AlertRuntimeConfig(this);
        }
    }
}
