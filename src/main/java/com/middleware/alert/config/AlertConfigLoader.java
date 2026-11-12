package com.middleware.alert.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 启动加载与手动刷新 alert_config。
 *
 * <p>加载失败时保留当前快照（首次为 AlertProperties 兜底），应用继续启动。</p>
 */
public class AlertConfigLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AlertConfigLoader.class);

    private final AlertConfigRepository repository;
    private final AlertConfigParser parser;
    private final AlertConfigProvider provider;
    private final AlertProperties defaults;
    private final AlertConfigProperties configProperties;
    private final String appName;

    /** 最近一次成功加载的配置版本，供只读端点展示 */
    private volatile long loadedVersion;
    /** 最近一次成功加载的时间戳（毫秒） */
    private volatile long loadedAt;

    public AlertConfigLoader(AlertConfigRepository repository,
                             AlertConfigParser parser,
                             AlertConfigProvider provider,
                             AlertProperties defaults,
                             AlertConfigProperties configProperties,
                             String appName) {
        this.repository = repository;
        this.parser = parser;
        this.provider = provider;
        this.defaults = defaults;
        this.configProperties = configProperties;
        this.appName = appName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (configProperties.isSelfCheck()) {
            try {
                repository.selfCheck(appName);
            } catch (Exception e) {
                log.warn("alert_config self-check failed, app={}: {}", appName, e.getMessage());
            }
        }
        load("startup");
    }

    /**
     * 手动刷新当前实例配置。console 后续直接调用本方法。
     */
    public synchronized AlertConfigRefreshResult refresh() {
        return load("manual");
    }

    public long getLoadedVersion() {
        return loadedVersion;
    }

    public long getLoadedAt() {
        return loadedAt;
    }

    private AlertConfigRefreshResult load(String trigger) {
        if (!configProperties.isEnabled()) {
            log.info("alert config table disabled, using AlertProperties");
            return AlertConfigRefreshResult.skipped("disabled");
        }
        try {
            List<AlertConfigRow> rows = repository.findByAppName(appName);
            AlertRuntimeConfig next = parser.parse(rows, defaults);
            AlertRuntimeConfig previous = provider.get();
            provider.set(next);

            long version = maxVersion(rows);
            this.loadedVersion = version;
            this.loadedAt = System.currentTimeMillis();
            List<String> changed = diff(previous, next);
            if ("startup".equals(trigger)) {
                log.info("alert config loaded, app={}, items={}, version={}, changed={}",
                        appName, rows.size(), version, changed);
            } else {
                log.info("alert config refreshed, app={}, items={}, version={}, changed={}",
                        appName, rows.size(), version, changed);
            }
            return AlertConfigRefreshResult.success(version, changed);
        } catch (Exception e) {
            log.warn("alert config load failed ({}), keep current snapshot, app={}: {}",
                    trigger, appName, e.getMessage());
            return AlertConfigRefreshResult.failure(e.getMessage());
        }
    }

    private static long maxVersion(List<AlertConfigRow> rows) {
        long version = 0L;
        if (rows != null) {
            for (AlertConfigRow row : rows) {
                if (row.getVersion() > version) {
                    version = row.getVersion();
                }
            }
        }
        return version;
    }

    private static List<String> diff(AlertRuntimeConfig before, AlertRuntimeConfig after) {
        List<String> changed = new ArrayList<String>();
        if (before == null || after == null) {
            return changed;
        }
        Map<String, Object> oldValues = before.asMap();
        Map<String, Object> newValues = after.asMap();
        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            if (!Objects.equals(oldValues.get(entry.getKey()), entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }
}
