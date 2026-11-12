package com.middleware.alert.core;

import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintGeneratorTest {

    private final FingerprintGenerator generator =
            new FingerprintGenerator(TestSupport.context());

    @Test
    void sameExceptionFramesProduceSameFingerprint() {
        IllegalStateException first = new IllegalStateException("a");
        IllegalStateException second = new IllegalStateException("b");

        String fingerprint = generator.exception(AlertType.BIZ_EXCEPTION, first);

        assertThat(fingerprint)
                .isEqualTo(generator.exception(AlertType.BIZ_EXCEPTION, second))
                .hasSize(32);
    }

    @Test
    void differentExceptionTypeProducesDifferentFingerprint() {
        assertThat(generator.exception(AlertType.BIZ_EXCEPTION, new IllegalStateException("x")))
                .isNotEqualTo(generator.exception(AlertType.BIZ_EXCEPTION, new NullPointerException("x")));
    }

    @Test
    void differentUriProducesDifferentFingerprint() {
        assertThat(generator.of(AlertType.SLOW_REQUEST, "/api/a"))
                .isNotEqualTo(generator.of(AlertType.SLOW_REQUEST, "/api/b"));
    }

    @Test
    void differentAlertTypeProducesDifferentFingerprint() {
        assertThat(generator.of(AlertType.SLOW_REQUEST, "/api/a"))
                .isNotEqualTo(generator.of(AlertType.SLOW_SQL, "/api/a"));
    }
}
