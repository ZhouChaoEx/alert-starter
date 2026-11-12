package com.middleware.alert.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置刷新结果，供后续 console 接口展示。
 */
public class AlertConfigRefreshResult {

    private final boolean success;
    private final long version;
    private final List<String> changedKeys;
    private final String message;

    private AlertConfigRefreshResult(boolean success, long version,
                                     List<String> changedKeys, String message) {
        this.success = success;
        this.version = version;
        this.changedKeys = changedKeys == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(changedKeys));
        this.message = message;
    }

    public static AlertConfigRefreshResult success(long version, List<String> changedKeys) {
        return new AlertConfigRefreshResult(true, version, changedKeys, "ok");
    }

    public static AlertConfigRefreshResult failure(String message) {
        return new AlertConfigRefreshResult(false, 0L, null, message);
    }

    public static AlertConfigRefreshResult skipped(String message) {
        return new AlertConfigRefreshResult(true, 0L, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public long getVersion() {
        return version;
    }

    public List<String> getChangedKeys() {
        return changedKeys;
    }

    public String getMessage() {
        return message;
    }
}
