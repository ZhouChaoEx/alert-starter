package com.middleware.alert.it;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟 xk-report 现有的全局异常处理器：捕获所有 Exception 并返回 HTTP 200 + 业务错误码。
 *
 * <p>用于验证 alert-starter 在这种“异常被业务方吞掉”的场景下仍能拿到原始异常。</p>
 */
@RestControllerAdvice
public class SwallowingExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Map<String, Object> handle(Exception exception) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "500");
        body.put("message", exception.getMessage());
        return body;
    }
}
