package com.middleware.alert.core;

import com.middleware.alert.AlertEvent;
import com.middleware.alert.AlertLevel;
import com.middleware.alert.AlertType;
import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertWriterTest {

    @Test
    @SuppressWarnings("unchecked")
    void writesBatchAndFillsCreateTime() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertProperties properties = TestSupport.properties();
        properties.getDatasource().setSelfCheck(false);
        AlertWriter writer = new AlertWriter(jdbc, properties,
                TestSupport.configProvider(properties),
                publisherWith(new LinkedBlockingQueue<AlertEvent>()));

        AlertEvent event = event();
        writer.write(Collections.singletonList(event));

        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(jdbc).batchUpdate(anyString(), captor.capture());

        List<Object[]> args = captor.getValue();
        assertThat(args).hasSize(1);
        assertThat(args.get(0)[3]).isEqualTo("BIZ_EXCEPTION");
        assertThat(args.get(0)[23]).isNotNull();
        assertThat(event.getCreateTime()).isNotNull();
        assertThat(writer.getWrittenCount()).isEqualTo(1);
        assertThat(writer.getDroppedCount()).isZero();
    }

    @Test
    void retriesThenDropsWhenWriteKeepsFailing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.batchUpdate(anyString(), anyList())).thenThrow(new RuntimeException("db down"));

        AlertProperties properties = TestSupport.properties();
        properties.getDatasource().setSelfCheck(false);
        AlertWriter writer = new AlertWriter(jdbc, properties,
                TestSupport.configProvider(properties),
                publisherWith(new LinkedBlockingQueue<AlertEvent>()));

        writer.write(Collections.singletonList(event()));

        // 首次 + 3 次重试
        verify(jdbc, times(4)).batchUpdate(anyString(), anyList());
        assertThat(writer.getWrittenCount()).isZero();
        assertThat(writer.getDroppedCount()).isEqualTo(1);
        assertThat(writer.getFailedBatches()).isEqualTo(1);
    }

    @Test
    void asyncLoopConsumesQueuedEvents() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertProperties properties = TestSupport.properties();
        properties.getDatasource().setSelfCheck(false);
        properties.getBatch().setIntervalMs(20L);
        BlockingQueue<AlertEvent> queue = new LinkedBlockingQueue<AlertEvent>();
        AlertWriter writer = new AlertWriter(jdbc, properties,
                TestSupport.configProvider(properties), publisherWith(queue));

        writer.start();
        try {
            queue.put(event());
            long deadline = System.currentTimeMillis() + 3000L;
            while (writer.getWrittenCount() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L);
            }
        } finally {
            writer.stop();
        }

        assertThat(writer.getWrittenCount()).isEqualTo(1);
        verify(jdbc, atLeastOnce()).batchUpdate(anyString(), anyList());
    }

    private static AlertPublisher publisherWith(BlockingQueue<AlertEvent> queue) {
        AlertPublisher publisher = mock(AlertPublisher.class);
        when(publisher.getQueue()).thenReturn(queue);
        return publisher;
    }

    private static AlertEvent event() {
        return AlertEvent.builder()
                .appName("test-app")
                .env("test")
                .instanceIp("127.0.0.1")
                .alertType(AlertType.BIZ_EXCEPTION)
                .alertLevel(AlertLevel.ERROR)
                .title("boom")
                .content("boom")
                .exceptionType(IllegalStateException.class.getName())
                .stackTrace("stack")
                .requestUri("/orders")
                .fingerprint("fp-writer")
                .build();
    }
}
