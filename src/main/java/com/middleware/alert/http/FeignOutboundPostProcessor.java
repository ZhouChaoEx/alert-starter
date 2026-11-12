package com.middleware.alert.http;

import com.middleware.alert.config.AlertConfigProvider;
import com.middleware.alert.config.AlertRuntimeConfig;
import feign.FeignException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;

/**
 * Feign 出站采集：用 JDK 代理包装 {@code @FeignClient} 接口。
 *
 * <p>为什么不包装 {@code feign.Client}：Spring Cloud 2.2 的 {@code FeignClientFactoryBean}
 * 不使用 Feign 的 {@code Capability} 扩展点，直接提供 {@code Client} Bean 会覆盖
 * {@code LoadBalancerFeignClient}，导致注册中心负载均衡失效。代理接口的方式不进入 Feign 内部，
 * 对负载均衡、重试、编解码都无影响。</p>
 *
 * <p>状态码来源：成功调用拿不到真实状态码（Feign 只在异常里暴露），因此成功路径以
 * {@code status=0}（未知）上报，只参与"慢调用"判定；异常路径从 {@link FeignException#status()} 取。</p>
 *
 * <p>代理<b>始终</b>创建，开关由 {@link OutboundReporter} 每次调用时读运行时快照判定，
 * 保证管理页面开启后免重启。</p>
 */
public class FeignOutboundPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(FeignOutboundPostProcessor.class);

    private final OutboundReporter reporter;
    private final AlertConfigProvider configProvider;

    public FeignOutboundPostProcessor(OutboundReporter reporter,
                                      AlertConfigProvider configProvider) {
        this.reporter = reporter;
        this.configProvider = configProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean == null) {
            return null;
        }
        Class<?> feignInterface = findFeignInterface(bean.getClass());
        if (feignInterface == null) {
            return bean;
        }
        try {
            return createProxy(bean, feignInterface);
        } catch (Throwable t) {
            log.warn("alert feign outbound proxy failed, bean={}: {}", beanName, t.toString());
            return bean;
        }
    }

    private static Class<?> findFeignInterface(Class<?> type) {
        for (Class<?> candidate : type.getInterfaces()) {
            if (AnnotatedElementUtils.findMergedAnnotation(candidate, FeignClient.class) != null) {
                return candidate;
            }
        }
        return null;
    }

    private Object createProxy(Object target, Class<?> feignInterface) {
        FeignClient feignClient = AnnotatedElementUtils.findMergedAnnotation(
                feignInterface, FeignClient.class);
        String service = resolveService(feignClient);
        String classPath = normalize(feignClient == null ? null : feignClient.path());

        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(feignInterface);
        factory.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                Method method = invocation.getMethod();
                String path = classPath + resolvePath(method);
                String url = "http://" + service + path;
                String httpMethod = resolveHttpMethod(method);
                AlertRuntimeConfig config = configProvider.get();

                long start = System.currentTimeMillis();
                try {
                    Object result = invocation.proceed();
                    long cost = System.currentTimeMillis() - start;
                    reporter.reportIfNeeded(url, service, path, httpMethod, 0, cost, null, config);
                    return result;
                } catch (Throwable t) {
                    long cost = System.currentTimeMillis() - start;
                    int status = t instanceof FeignException ? ((FeignException) t).status() : -1;
                    reporter.reportIfNeeded(url, service, path, httpMethod, status, cost, t, config);
                    throw t;
                }
            }
        });
        return factory.getProxy();
    }

    private static String resolveService(FeignClient feignClient) {
        if (feignClient == null) {
            return "";
        }
        String name = feignClient.name();
        if (name == null || name.isEmpty()) {
            name = feignClient.value();
        }
        return name == null ? "" : name;
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        String result = path.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String resolvePath(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return "";
        }
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length > 0 && paths[0] != null ? paths[0] : "";
    }

    private static String resolveHttpMethod(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return null;
        }
        RequestMethod[] methods = mapping.method();
        return methods.length > 0 ? methods[0].name() : null;
    }
}
