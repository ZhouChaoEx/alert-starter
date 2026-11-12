package com.middleware.alert.core;

import com.middleware.alert.config.AlertProperties;
import org.springframework.core.env.Environment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 应用级公共上下文，所有告警事件共用。
 */
public class AlertContext {

    private final String appName;
    private final String env;
    private final String instanceIp;

    public AlertContext(AlertProperties properties, Environment environment) {
        this.appName = firstNonBlank(
                properties.getAppName(),
                environment.getProperty("spring.application.name"),
                "unknown");
        this.env = firstNonBlank(
                properties.getEnv(),
                environment.getProperty("spring.profiles.active"),
                "default");
        this.instanceIp = resolveInstanceIp();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "unknown";
    }

    private static String resolveInstanceIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getAppName() {
        return appName;
    }

    public String getEnv() {
        return env;
    }

    public String getInstanceIp() {
        return instanceIp;
    }
}
