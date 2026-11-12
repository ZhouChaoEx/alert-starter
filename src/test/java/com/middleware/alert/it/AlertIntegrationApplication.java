package com.middleware.alert.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试用的最小 Web 应用，模拟 xk-report 的接入方式：
 * Spring Boot 2.3.7 + Web + alert-starter 自动装配。
 */
@SpringBootApplication
public class AlertIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertIntegrationApplication.class, args);
    }
}
