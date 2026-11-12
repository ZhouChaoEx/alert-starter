package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;

import java.util.List;

/**
 * 告警落库出口。M1 由 {@code AlertWriter} 实现；没有可用数据源时不存在实现，
 * 发布器会降级为只打日志。
 */
@FunctionalInterface
public interface AlertSink {

    /**
     * 写入一批事件，不允许抛出异常。
     */
    void write(List<AlertEvent> events);
}
