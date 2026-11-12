package com.middleware.alert.config;

import com.middleware.alert.TestSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertConfigLoaderTest {

    private final AlertProperties defaults = TestSupport.properties();
    private final AlertConfigProperties configProperties = new AlertConfigProperties();
    private final AlertConfigRepository repository = mock(AlertConfigRepository.class);
    private final AlertConfigProvider provider =
            new AlertConfigProvider(AlertRuntimeConfig.from(defaults));
    private final AlertConfigLoader loader = new AlertConfigLoader(
            repository, new AlertConfigParser(), provider, defaults, configProperties, "test-app");

    @Test
    void refreshLoadsTableAndReplacesSnapshot() {
        when(repository.findByAppName("test-app")).thenReturn(Arrays.asList(
                new AlertConfigRow("slow-request.threshold-ms", "2000", "LONG", 5L),
                new AlertConfigRow("biz.enabled", "false", "BOOL", 5L)
        ));

        AlertConfigRefreshResult result = loader.refresh();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVersion()).isEqualTo(5L);
        assertThat(result.getChangedKeys()).contains("slow-request.threshold-ms", "biz.enabled");
        assertThat(provider.get().getSlowRequestThresholdMs()).isEqualTo(2000L);
        assertThat(provider.get().isBizEnabled()).isFalse();
    }

    @Test
    void loadFailureKeepsPreviousSnapshot() {
        when(repository.findByAppName("test-app"))
                .thenThrow(new RuntimeException("table not found"));

        AlertConfigRefreshResult result = loader.refresh();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("table not found");
        assertThat(provider.get().getSlowRequestThresholdMs())
                .isEqualTo(defaults.getSlowRequest().getThresholdMs());
    }

    @Test
    void disabledConfigSkipsRepository() {
        configProperties.setEnabled(false);

        AlertConfigRefreshResult result = loader.refresh();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("disabled");
        verify(repository, never()).findByAppName("test-app");
    }

    @Test
    void emptyTableFallsBackToDefaults() {
        when(repository.findByAppName("test-app"))
                .thenReturn(Collections.<AlertConfigRow>emptyList());

        AlertConfigRefreshResult result = loader.refresh();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVersion()).isZero();
        assertThat(provider.get().getSlowRequestThresholdMs())
                .isEqualTo(defaults.getSlowRequest().getThresholdMs());
    }
}
