package com.middleware.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * alert-starter 全部配置项，前缀 {@code alert}。
 */
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    /** 全局开关 */
    private boolean enabled = false;

    /** 应用名，缺省取 spring.application.name */
    private String appName;

    /** 环境标识，缺省取 spring.profiles.active */
    private String env;

    /** 写入模式：ASYNC（默认）/ SYNC */
    private WriteMode writeMode = WriteMode.ASYNC;

    private final StackTrace stackTrace = new StackTrace();
    private final Datasource datasource = new Datasource();
    private final Queue queue = new Queue();
    private final Batch batch = new Batch();
    private final Dedup dedup = new Dedup();
    private final RateLimit rateLimit = new RateLimit();
    private final SlowRequest slowRequest = new SlowRequest();
    private final Async async = new Async();
    private final SlowSql slowSql = new SlowSql();
    private final Memory memory = new Memory();
    private final Http http = new Http();
    private final Biz biz = new Biz();
    private final Error error = new Error();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public WriteMode getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode;
    }

    public StackTrace getStackTrace() {
        return stackTrace;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public Queue getQueue() {
        return queue;
    }

    public Batch getBatch() {
        return batch;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public SlowRequest getSlowRequest() {
        return slowRequest;
    }

    public Async getAsync() {
        return async;
    }

    public SlowSql getSlowSql() {
        return slowSql;
    }

    public Memory getMemory() {
        return memory;
    }

    public Http getHttp() {
        return http;
    }

    public Biz getBiz() {
        return biz;
    }

    public Error getError() {
        return error;
    }

    public enum WriteMode {
        ASYNC, SYNC
    }

    public static class StackTrace {
        /** 堆栈最大字符数，超出截断 */
        private int maxLength = 8192;

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }
    }

    public static class Datasource {
        /** 告警库 JDBC 地址，为空时不启用写入 */
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.kingbase8.Driver";
        private final Hikari hikari = new Hikari();
        /** 启动时做一次只读自检，失败仅告警 */
        private boolean selfCheck = true;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Hikari getHikari() {
            return hikari;
        }

        public boolean isSelfCheck() {
            return selfCheck;
        }

        public void setSelfCheck(boolean selfCheck) {
            this.selfCheck = selfCheck;
        }
    }

    public static class Hikari {
        private int maximumPoolSize = 2;
        private int minimumIdle = 1;
        private long connectionTimeoutMs = 3000;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }

    public static class Queue {
        /** 有界队列容量 */
        private int capacity = 10000;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }

    public static class Batch {
        /** 单批最大条数 */
        private int size = 50;
        /** 批量等待间隔（毫秒） */
        private long intervalMs = 1000L;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }

    public static class Dedup {
        /** 去重窗口（秒） */
        private int windowSeconds = 300;
        /** 窗口内指纹条目上限，超出淘汰最旧 */
        private int maxEntries = 10000;

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    public static class RateLimit {
        /** 每个告警类型每分钟上限 */
        private int perMinute = 20;
        /** 超限后每 sampleFactor 条放行 1 条 */
        private int sampleFactor = 10;

        public int getPerMinute() {
            return perMinute;
        }

        public void setPerMinute(int perMinute) {
            this.perMinute = perMinute;
        }

        public int getSampleFactor() {
            return sampleFactor;
        }

        public void setSampleFactor(int sampleFactor) {
            this.sampleFactor = sampleFactor;
        }
    }

    public static class SlowRequest {
        private boolean enabled = true;
        private long thresholdMs = 1000L;
        private List<String> ignorePaths = new ArrayList<String>(Arrays.asList(
                "/actuator/**",
                "/health",
                "/favicon.ico",
                "/swagger-ui/**",
                "/v2/api-docs",
                "/webjars/**"
        ));
        /** 同 URI 聚合窗口（秒），0 表示不聚合 */
        private int aggregateWindowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getThresholdMs() {
            return thresholdMs;
        }

        public void setThresholdMs(long thresholdMs) {
            this.thresholdMs = thresholdMs;
        }

        public List<String> getIgnorePaths() {
            return ignorePaths;
        }

        public void setIgnorePaths(List<String> ignorePaths) {
            this.ignorePaths = ignorePaths;
        }

        public int getAggregateWindowSeconds() {
            return aggregateWindowSeconds;
        }

        public void setAggregateWindowSeconds(int aggregateWindowSeconds) {
            this.aggregateWindowSeconds = aggregateWindowSeconds;
        }
    }

    /**
     * 异步异常采集。默认关闭，需显式开启 {@code alert.async.enabled=true}。
     */
    public static class Async {
        private boolean enabled = false;
        /** 是否采集方法参数类型列表（只记类型，不记值） */
        private boolean argsTypes = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isArgsTypes() {
            return argsTypes;
        }

        public void setArgsTypes(boolean argsTypes) {
            this.argsTypes = argsTypes;
        }
    }

    /**
     * 慢 SQL 采集。默认关闭，需显式开启 {@code alert.slow-sql.enabled=true}。
     */
    public static class SlowSql {
        private boolean enabled = false;
        private long thresholdMs = 500L;
        /** SQL 文本最大字符数，超出截断 */
        private int maxSqlLength = 2048;
        /** 排除的 mapper 前缀，命中前缀的语句不采集 */
        private List<String> excludeMapperPrefixes = new ArrayList<String>();
        /** 同 mapperId + SQL 指纹的聚合窗口（秒），0 表示不聚合 */
        private int aggregateWindowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getThresholdMs() {
            return thresholdMs;
        }

        public void setThresholdMs(long thresholdMs) {
            this.thresholdMs = thresholdMs;
        }

        public int getMaxSqlLength() {
            return maxSqlLength;
        }

        public void setMaxSqlLength(int maxSqlLength) {
            this.maxSqlLength = maxSqlLength;
        }

        public List<String> getExcludeMapperPrefixes() {
            return excludeMapperPrefixes;
        }

        public void setExcludeMapperPrefixes(List<String> excludeMapperPrefixes) {
            this.excludeMapperPrefixes = excludeMapperPrefixes;
        }

        public int getAggregateWindowSeconds() {
            return aggregateWindowSeconds;
        }

        public void setAggregateWindowSeconds(int aggregateWindowSeconds) {
            this.aggregateWindowSeconds = aggregateWindowSeconds;
        }
    }

    /**
     * 内存 / OOM 前兆采集。默认关闭，需显式开启 {@code alert.memory.enabled=true}。
     */
    public static class Memory {
        private boolean enabled = false;
        /** 采样间隔（毫秒） */
        private long intervalMs = 60000L;
        /** 老年代 GC 后使用率阈值（0~1） */
        private double oldGenRatio = 0.85D;
        /** 连续采样次数：堆使用率持续上升且未发生 GC 时触发 */
        private int consecutiveSamples = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public double getOldGenRatio() {
            return oldGenRatio;
        }

        public void setOldGenRatio(double oldGenRatio) {
            this.oldGenRatio = oldGenRatio;
        }

        public int getConsecutiveSamples() {
            return consecutiveSamples;
        }

        public void setConsecutiveSamples(int consecutiveSamples) {
            this.consecutiveSamples = consecutiveSamples;
        }
    }

    public static class Biz {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Error {
        private final Npe npe = new Npe();
        private final StackOverflow stackOverflow = new StackOverflow();

        public Npe getNpe() {
            return npe;
        }

        public StackOverflow getStackOverflow() {
            return stackOverflow;
        }
    }

    public static class Npe {
        private boolean enabled = true;
        /** 采样率 0~1 */
        private double sampleRate = 1.0D;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
        }
    }

    public static class StackOverflow {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Http {

        private final Outbound outbound = new Outbound();

        public Outbound getOutbound() {
            return outbound;
        }
    }

    /**
     * 出站 HTTP 采集。默认关闭，需显式开启 {@code alert.http.outbound.enabled=true}。
     */
    public static class Outbound {

        private boolean enabled = false;
        /** 慢调用阈值（毫秒） */
        private long thresholdMs = 3000L;
        /** 是否采集"调用成功但耗时超过阈值"的慢调用 */
        private boolean slowEnabled = true;
        /** 视为异常的下游状态码 */
        private List<Integer> statuses = new ArrayList<Integer>(Arrays.asList(502, 503, 504));
        /** 排除的主机，支持通配，如 *.internal.com */
        private List<String> excludeHosts = new ArrayList<String>();
        /** 排除的路径，支持 Ant 风格，如 /actuator/** */
        private List<String> excludePaths = new ArrayList<String>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getThresholdMs() {
            return thresholdMs;
        }

        public void setThresholdMs(long thresholdMs) {
            this.thresholdMs = thresholdMs;
        }

        public boolean isSlowEnabled() {
            return slowEnabled;
        }

        public void setSlowEnabled(boolean slowEnabled) {
            this.slowEnabled = slowEnabled;
        }

        public List<Integer> getStatuses() {
            return statuses;
        }

        public void setStatuses(List<Integer> statuses) {
            this.statuses = statuses;
        }

        public List<String> getExcludeHosts() {
            return excludeHosts;
        }

        public void setExcludeHosts(List<String> excludeHosts) {
            this.excludeHosts = excludeHosts;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }
}
