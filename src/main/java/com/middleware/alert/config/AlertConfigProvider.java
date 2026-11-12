package com.middleware.alert.config;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 动态配置快照提供者。
 *
 * <p>采集点只读内存快照，不查库；刷新时由 {@link AlertConfigLoader} 原子替换。</p>
 */
public class AlertConfigProvider {

    private final AtomicReference<AlertRuntimeConfig> current;

    public AlertConfigProvider(AlertRuntimeConfig initial) {
        this.current = new AtomicReference<AlertRuntimeConfig>(initial);
    }

    public AlertRuntimeConfig get() {
        return current.get();
    }

    public void set(AlertRuntimeConfig config) {
        if (config != null) {
            current.set(config);
        }
    }
}
