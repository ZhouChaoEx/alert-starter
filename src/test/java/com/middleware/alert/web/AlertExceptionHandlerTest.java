package com.middleware.alert.web;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import com.middleware.alert.config.AlertRuntimeConfig;
import com.middleware.alert.core.AlertPublisher;
import com.middleware.alert.exception.AlertableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AlertExceptionHandlerTest {

    private final AlertProperties properties = TestSupport.properties();
    private final AlertPublisher publisher = TestSupport.publisher(properties, null);
    private final AlertExceptionHandler handler =
            new AlertExceptionHandler(publisher, publisher.getConfigProvider());
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");

    @AfterEach
    void clearThreadState() {
        AlertExceptionHandler.clearThreadState();
    }

    @Test
    void alertableExceptionIsPublishedWithCode() {
        handler.handle(new AlertableException("order failed", "E100"), request);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.BIZ_EXCEPTION);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.ERROR);
        assertThat(event.getTitle()).contains("[E100]").contains("order failed");
        assertThat(event.getRequestUri()).isEqualTo("/orders");
        assertThat(event.getHttpMethod()).isEqualTo("POST");
    }

    @Test
    void stackOverflowIsCritical() {
        handler.handle(new StackOverflowError("stack overflow"), request);

        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.STACK_OVERFLOW);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.CRITICAL);
        assertThat(event.getStackTrace()).isNotNull();
    }

    @Test
    void nullPointerIsPublished() {
        handler.handle(new NullPointerException("npe"), request);

        AlertEvent event = publisher.getQueue().peek();
        assertThat(event.getAlertType()).isEqualTo(AlertType.NULL_POINTER);
        assertThat(event.getAlertLevel()).isEqualTo(AlertLevel.ERROR);
    }

    @Test
    void otherExceptionIsIgnored() {
        handler.handle(new IllegalStateException("ignored"), request);

        assertThat(publisher.getQueueSize()).isZero();
    }

    @Test
    void sameThrowableIsReportedOnlyOnce() {
        AlertableException exception = new AlertableException("boom", "E200");

        handler.handle(exception, request);
        handler.handle(exception, request);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
    }

    @Test
    void nestedControllerCallDoesNotReportTwice() {
        AlertableException exception = new AlertableException("boom", "E201");

        AlertExceptionHandler.enter();   // 外层 Controller
        AlertExceptionHandler.enter();   // 内层 Controller
        handler.handle(exception, request);
        AlertExceptionHandler.exit();    // 内层退出，不清理已上报集合
        handler.handle(exception, request);
        AlertExceptionHandler.exit();    // 外层退出

        assertThat(publisher.getQueueSize()).isEqualTo(1);
    }

    @Test
    void disabledBizSwitchStopsPublishing() {
        publisher.getConfigProvider().set(
                AlertRuntimeConfig.builder(properties).bizEnabled(false).build());

        handler.handle(new AlertableException("boom", "E300"), request);

        assertThat(publisher.getQueueSize()).isZero();
    }
}
