package com.middleware.alert.web;

import com.middleware.alert.config.AlertConfigLoader;
import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertConfigRefreshResult;
import com.middleware.alert.core.AlertContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采集侧本地端点：刷新配置 + 只读当前实例生效值。
 *
 * <p>这两个端点由 <b>alert-starter</b> 提供，因此所有接入告警采集的服务都具备，
 * 不需要额外引入 {@code alert-console-starter}。管理端（xk-report 等）通过
 * {@code console.instances} 里的地址远程调用。</p>
 *
 * <ul>
 *   <li>{@code POST /alert/refresh/local}：重新加载 {@code alert_config}，返回版本与变更项；</li>
 *   <li>{@code GET /alert/effective}：返回当前实例内存快照里的生效值（只读）。
 *       内容仅包含阈值/开关，<b>不含数据源密码</b>等敏感配置。</li>
 * </ul>
 *
 * <p>与现有设计一致：无鉴权，仅限内网；业务应用的鉴权拦截器需放行 {@code /alert/**}。</p>
 */
@RestController
@RequestMapping("/alert")
public class AlertLocalController {

    private final ObjectProvider<AlertConfigLoader> loaderProvider;
    private final AlertConfigProvider configProvider;
    private final AlertContext alertContext;

    public AlertLocalController(ObjectProvider<AlertConfigLoader> loaderProvider,
                                AlertConfigProvider configProvider,
                                AlertContext alertContext) {
        this.loaderProvider = loaderProvider;
        this.configProvider = configProvider;
        this.alertContext = alertContext;
    }

    @PostMapping("/refresh/local")
    public Map<String, Object> refreshLocal() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        AlertConfigLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            body.put("success", false);
            body.put("message", "配置表未启用（alert.config.enabled=false 或缺少数据源）");
            body.put("version", 0L);
            return body;
        }
        AlertConfigRefreshResult result = loader.refresh();
        body.put("success", result.isSuccess());
        body.put("message", result.getMessage());
        body.put("version", result.getVersion());
        body.put("changed", result.getChangedKeys());
        return body;
    }

    @GetMapping("/effective")
    public Map<String, Object> effective() {
        AlertConfigLoader loader = loaderProvider.getIfAvailable();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", true);
        body.put("appName", alertContext.getAppName());
        body.put("version", loader == null ? 0L : loader.getLoadedVersion());
        body.put("loadedAt", loader == null ? 0L : loader.getLoadedAt());
        body.put("effective", configProvider.get().asMap());
        return body;
    }
}
