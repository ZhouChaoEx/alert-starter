package com.middleware.alert.core;

import com.middleware.alert.AlertType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 告警指纹生成器。
 *
 * <p>指纹 = md5(appName + "|" + alertType + "|" + 归一化特征)，固定 32 位小写十六进制。</p>
 */
public class FingerprintGenerator {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int MAX_STACK_FRAMES = 5;

    private final AlertContext context;

    public FingerprintGenerator(AlertContext context) {
        this.context = context;
    }

    /**
     * 通用指纹。
     *
     * @param type    告警类型
     * @param feature 归一化特征，例如 URI 模板、mapperId + SQL、目标 URL
     */
    public String of(AlertType type, String feature) {
        String raw = context.getAppName() + "|" + type.name() + "|"
                + (feature == null ? "" : feature);
        return md5(raw);
    }

    /**
     * 异常指纹：异常类名 + 栈顶 5 帧的方法签名。
     */
    public String exception(AlertType type, Throwable throwable) {
        return of(type, exceptionFeature(throwable));
    }

    static String exceptionFeature(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(throwable.getClass().getName());
        StackTraceElement[] stack = throwable.getStackTrace();
        if (stack != null) {
            int limit = Math.min(MAX_STACK_FRAMES, stack.length);
            for (int i = 0; i < limit; i++) {
                StackTraceElement element = stack[i];
                sb.append('|')
                        .append(element.getClassName())
                        .append('.')
                        .append(element.getMethodName());
            }
        }
        return sb.toString();
    }

    static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int value2 = bytes[i] & 0xFF;
                out[i * 2] = HEX[value2 >>> 4];
                out[i * 2 + 1] = HEX[value2 & 0x0F];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 MD5，这里只做防御
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
