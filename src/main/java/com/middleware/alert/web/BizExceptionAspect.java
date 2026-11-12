package com.middleware.alert.web;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Controller 层异常采集切面。
 *
 * <p>必须在业务 {@code @ControllerAdvice} 之前拿到原始异常：AOP 位于 handler 调用处，
 * 异常先经过这里再交给全局异常处理器，因此即使业务 advice 把异常转成 HTTP 200，
 * 也不会漏报。</p>
 */
@Aspect
public class BizExceptionAspect {

    private static final Logger log = LoggerFactory.getLogger(BizExceptionAspect.class);

    private final AlertExceptionHandler handler;

    public BizExceptionAspect(AlertExceptionHandler handler) {
        this.handler = handler;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)"
            + " || within(@org.springframework.stereotype.Controller *)"
            + " || @annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        AlertExceptionHandler.enter();
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            try {
                handler.handle(throwable, currentRequest());
            } catch (Throwable t) {
                log.warn("alert aspect failed: {}", t.toString());
            }
            throw throwable;
        } finally {
            AlertExceptionHandler.exit();
        }
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }
}
