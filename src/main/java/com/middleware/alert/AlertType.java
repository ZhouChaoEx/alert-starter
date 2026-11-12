package com.middleware.alert;

/**
 * 告警类型。
 *
 * <p>M1 阶段实际产出 {@link #BIZ_EXCEPTION}、{@link #SLOW_REQUEST}、
 * {@link #STACK_OVERFLOW}、{@link #NULL_POINTER} 四类，其余枚举为后续里程碑预留。</p>
 */
public enum AlertType {

    BIZ_EXCEPTION("业务异常"),
    SLOW_REQUEST("慢请求"),
    ASYNC_EXCEPTION("异步异常"),
    HTTP_OUTBOUND("出站HTTP异常"),
    OOM_WARNING("内存溢出前兆"),
    SLOW_SQL("慢SQL"),
    STACK_OVERFLOW("栈溢出"),
    NULL_POINTER("空指针"),

    ;
    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
