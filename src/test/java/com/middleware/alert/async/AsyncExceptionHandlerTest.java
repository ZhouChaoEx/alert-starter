package com.middleware.alert.async;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.core.AlertPublisher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExceptionHandlerTest {

    private static final Method DO_WORK = doWorkMethod();

    private static Method doWorkMethod() {
        try {
            return SampleService.class.getMethod("doWork", String.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void disabledByDefault() {
        AlertPublisher publisher = publisher(false, true);

        handler(publisher).handleUncaughtException(
                new IllegalStateException("boom"), DO_WORK, "order-1", 42);

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void publishesSignatureAndExceptionType() {
        AlertPublisher publisher = publisher(true, true);

        handler(publisher).handleUncaughtException(
                new IllegalStateException("boom"), DO_WORK, "order-1", 42);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.ASYNC_EXCEPTION);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(event.getTitle()).contains("SampleService#doWork(String,int)").contains("boom");
        assertThat(event.getExceptionType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(event.getStackTrace()).contains("IllegalStateException");
        assertThat(event.getFingerprint()).hasSize(32);
    }

    @Test
    void signatureOmitsArgumentTypesWhenDisabled() {
        AlertPublisher publisher = publisher(true, false);

        handler(publisher).handleUncaughtException(
                new IllegalStateException("boom"), DO_WORK, "order-1", 42);

        String title = publisher.getQueue().peek().getTitle();
        assertThat(title).contains("SampleService#doWork")
                .doesNotContain("(String,int)");
    }

    @Test
    void argumentValuesNeverAppearInTitle() {
        AlertPublisher publisher = publisher(true, true);

        handler(publisher).handleUncaughtException(
                new IllegalStateException("boom"), DO_WORK, "secret-order-no", 998877);

        String title = publisher.getQueue().peek().getTitle();
        assertThat(title).doesNotContain("secret-order-no").doesNotContain("998877");
    }

    @Test
    void nullThrowableIsIgnored() {
        AlertPublisher publisher = publisher(true, true);

        handler(publisher).handleUncaughtException(null, DO_WORK, "order-1", 42);

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void signatureOfNullMethodIsUnknown() {
        assertThat(AsyncExceptionHandler.signature(null, true)).isEqualTo("unknown");
    }

    private static AsyncExceptionHandler handler(AlertPublisher publisher) {
        return new AsyncExceptionHandler(publisher, publisher.getConfigProvider());
    }

    private static AlertPublisher publisher(boolean enabled, boolean argsTypes) {
        AlertProperties properties = TestSupport.properties();
        properties.getAsync().setEnabled(enabled);
        properties.getAsync().setArgsTypes(argsTypes);
        return TestSupport.publisher(properties, null);
    }

    @SuppressWarnings("unused")
    static class SampleService {
        public void doWork(String orderNo, int count) {
            // 仅供方法签名反射使用
        }
    }
}
