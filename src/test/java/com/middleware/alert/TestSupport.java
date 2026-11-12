package com.middleware.alert;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertContext;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.core.AlertSink;
import com.middleware.alert.core.DedupRegistry;
import com.middleware.alert.core.FingerprintGenerator;
import com.middleware.alert.core.RateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试公共构造。
 */
public final class TestSupport {

    private TestSupport() {
    }

    public static AlertProperties properties() {
        return new AlertProperties();
    }

    public static AlertContext context() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "test-app");
        environment.setProperty("spring.profiles.active", "test");
        return new AlertContext(properties(), environment);
    }

    public static AlertConfigProvider configProvider(AlertProperties properties) {
        return new AlertConfigProvider(AlertRuntimeConfig.from(properties));
    }

    @SuppressWarnings("unchecked")
    public static AlertPublisher publisher(AlertProperties properties, AlertSink sink) {
        AlertContext context = context();
        AlertConfigProvider configProvider = configProvider(properties);
        ObjectProvider<AlertSink> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sink);
        return new AlertPublisher(properties, configProvider, context,
                new FingerprintGenerator(context),
                new DedupRegistry(configProvider),
                new RateLimiter(configProvider),
                provider);
    }

    /**
     * 可推进的时钟，用于去重窗口与限流窗口测试。
     */
    public static final class MutableClock extends Clock {

        private final AtomicLong millis;

        public MutableClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
        }

        public void advance(long deltaMillis) {
            millis.addAndGet(deltaMillis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public long millis() {
            return millis.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
