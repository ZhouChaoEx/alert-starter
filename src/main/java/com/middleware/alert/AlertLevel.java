package com.middleware.alert;

/**
 * 告警级别，数值越大越严重。
 */
public enum AlertLevel {

    INFO(0),
    WARN(1),
    ERROR(2),
    CRITICAL(3);

    private final int weight;

    AlertLevel(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isAtLeast(AlertLevel other) {
        return other != null && this.weight >= other.weight;
    }
}
