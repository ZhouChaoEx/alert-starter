package com.middleware.alert.core;

import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void limitsPerMinuteThenAllowsAfterWindowReset() {
        AlertProperties properties = TestSupport.properties();
        properties.getRateLimit().setPerMinute(2);
        properties.getRateLimit().setSampleFactor(3);
        TestSupport.MutableClock clock = new TestSupport.MutableClock(0L);
        RateLimiter limiter = new RateLimiter(TestSupport.configProvider(properties), clock);

        assertThat(limiter.allow(AlertType.BIZ_EXCEPTION)).isTrue();
        assertThat(limiter.allow(AlertType.BIZ_EXCEPTION)).isTrue();
        assertThat(limiter.allow(AlertType.BIZ_EXCEPTION)).isFalse();

        clock.advance(60_001L);
        assertThat(limiter.allow(AlertType.BIZ_EXCEPTION)).isTrue();
    }

    @Test
    void overLimitEventsAreSampled() {
        AlertProperties properties = TestSupport.properties();
        properties.getRateLimit().setPerMinute(1);
        properties.getRateLimit().setSampleFactor(3);
        TestSupport.MutableClock clock = new TestSupport.MutableClock(0L);
        RateLimiter limiter = new RateLimiter(TestSupport.configProvider(properties), clock);

        assertThat(limiter.allow(AlertType.NULL_POINTER)).isTrue();  // 1
        assertThat(limiter.allow(AlertType.NULL_POINTER)).isFalse(); // exceeded=1
        assertThat(limiter.allow(AlertType.NULL_POINTER)).isFalse(); // exceeded=2
        assertThat(limiter.allow(AlertType.NULL_POINTER)).isTrue();  // exceeded=3，采样放行
    }

    @Test
    void zeroLimitMeansUnlimited() {
        AlertProperties properties = TestSupport.properties();
        properties.getRateLimit().setPerMinute(0);
        RateLimiter limiter = new RateLimiter(TestSupport.configProvider(properties),
                new TestSupport.MutableClock(0L));

        for (int i = 0; i < 100; i++) {
            assertThat(limiter.allow(AlertType.SLOW_REQUEST)).isTrue();
        }
    }
}
