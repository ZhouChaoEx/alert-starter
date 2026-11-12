package com.middleware.alert.core;

import com.middleware.alert.AlertType;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按告警类型限流：固定分钟窗口。
 *
 * <p>窗口内超过 {@code per-minute} 后，每 {@code sample-factor} 条放行 1 条，
 * 既保留“仍在发生”的信号，又避免刷屏。</p>
 *
 * <p>限流参数每次从动态配置快照读取，支持刷新后生效。</p>
 */
public class RateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final AlertConfigProvider configProvider;
    private final Clock clock;
    private final ConcurrentHashMap<AlertType, Counter> counters =
            new ConcurrentHashMap<AlertType, Counter>();

    public RateLimiter(AlertConfigProvider configProvider) {
        this(configProvider, Clock.systemDefaultZone());
    }

    public RateLimiter(AlertConfigProvider configProvider, Clock clock) {
        this.configProvider = configProvider;
        this.clock = clock;
    }

    public boolean allow(AlertType type) {
        AlertRuntimeConfig config = configProvider.get();
        int perMinute = config.getRateLimitPerMinute();
        int sampleFactor = Math.max(1, config.getRateLimitSampleFactor());
        if (type == null || perMinute <= 0) {
            return true;
        }
        Counter counter = counters.computeIfAbsent(type, key -> new Counter(clock.millis()));
        synchronized (counter) {
            long now = clock.millis();
            if (now - counter.windowStart >= WINDOW_MS) {
                counter.windowStart = now;
                counter.count = 0;
                counter.exceeded = 0;
            }
            if (counter.count < perMinute) {
                counter.count++;
                return true;
            }
            counter.exceeded++;
            return counter.exceeded % sampleFactor == 0;
        }
    }

    private static final class Counter {
        private long windowStart;
        private int count;
        private int exceeded;

        private Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
