package com.middleware.alert.web;

import com.middleware.alert.TestSupport;
import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.core.AlertContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertLocalControllerTest {

    private final AlertConfigProvider configProvider =
            TestSupport.configProvider(TestSupport.properties());
    private final AlertContext context = TestSupport.context();

    @Test
    @SuppressWarnings("unchecked")
    void effectiveReturnsSnapshotAndVersion() {
        AlertConfigLoader loader = mock(AlertConfigLoader.class);
        when(loader.getLoadedVersion()).thenReturn(15L);
        when(loader.getLoadedAt()).thenReturn(1_700_000_000_000L);
        ObjectProvider<AlertConfigLoader> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(loader);

        Map<String, Object> body = new AlertLocalController(provider, configProvider, context).effective();

        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("appName")).isEqualTo("test-app");
        assertThat(body.get("version")).isEqualTo(15L);
        assertThat(body.get("loadedAt")).isEqualTo(1_700_000_000_000L);
        assertThat((Map<String, Object>) body.get("effective")).containsKey("runtime.enabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void effectiveWorksWithoutLoader() {
        ObjectProvider<AlertConfigLoader> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        Map<String, Object> body = new AlertLocalController(provider, configProvider, context).effective();

        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("version")).isEqualTo(0L);
        assertThat((Map<String, Object>) body.get("effective")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshLocalDelegatesToLoader() {
        AlertConfigLoader loader = mock(AlertConfigLoader.class);
        when(loader.refresh()).thenReturn(
                AlertConfigRefreshResult.success(16L, Arrays.asList("batch.size")));
        ObjectProvider<AlertConfigLoader> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(loader);

        Map<String, Object> body = new AlertLocalController(provider, configProvider, context).refreshLocal();

        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("version")).isEqualTo(16L);
        assertThat((Iterable<String>) body.get("changed")).containsExactly("batch.size");
    }

    @Test
    void refreshLocalReportsMissingLoader() {
        ObjectProvider<AlertConfigLoader> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        Map<String, Object> body = new AlertLocalController(provider, configProvider, context).refreshLocal();

        assertThat(body.get("success")).isEqualTo(false);
        assertThat(String.valueOf(body.get("message"))).contains("配置表未启用");
    }
}
