-- =============================================================================
-- alert-starter 告警表 DDL
-- 数据库 : MySQL（8.0 验证，5.7 兼容）
-- 执行方式: 由 DBA 手工执行，starter 不自动建表
-- 版本   : v0.2-mysql
-- 说明   : 应用账号只需 INSERT 权限；Zabbix 侧读取账号需 SELECT 权限
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 建议：若各服务使用独立库，请先切换 database 再执行，例如
--   USE alert;
-- 表名固定为 alert_info，不带库名前缀。
-- -----------------------------------------------------------------------------

CREATE TABLE alert_info (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    app_name        VARCHAR(128)    NOT NULL COMMENT '应用名，取自 spring.application.name',
    env             VARCHAR(32)     COMMENT '环境，取自 spring.profiles.active，缺省 default',
    instance_ip     VARCHAR(64)     COMMENT '实例 IP',
    alert_type      VARCHAR(32)     NOT NULL COMMENT '告警类型：BIZ_EXCEPTION/SLOW_REQUEST/ASYNC_EXCEPTION/HTTP_OUTBOUND/OOM_WARNING/SLOW_SQL/STACK_OVERFLOW/NULL_POINTER/THREAD_POOL_SATURATED/CONNECTION_POOL_EXHAUSTED',
    alert_level     VARCHAR(16)     NOT NULL COMMENT '告警级别：INFO/WARN/ERROR/CRITICAL',
    title           VARCHAR(512)    COMMENT '告警标题，最长 512 字符',
    content         TEXT            COMMENT '告警描述',
    exception_type  VARCHAR(256)    COMMENT '异常全限定类名',
    stack_trace     TEXT            COMMENT '异常堆栈，按配置截断（默认 8192 字符）',
    request_uri     VARCHAR(512)    COMMENT '触发告警的请求 URI 模板（不含参数值）',
    target_url      VARCHAR(512)    COMMENT '出站调用目标 URL，仅 scheme+host+path，已剔除 query',
    http_method     VARCHAR(16)     COMMENT 'HTTP 方法',
    http_status     INT             COMMENT 'HTTP 状态码（出站场景为下游返回码）',
    cost_ms         BIGINT          COMMENT '耗时（毫秒），含义随告警类型而定',
    sql_text        TEXT            COMMENT '慢 SQL 语句，参数保持 ? 占位，按配置截断',
    mapper_id       VARCHAR(256)    COMMENT 'MyBatis MappedStatement id',
    pool_name       VARCHAR(128)    COMMENT '线程池 Bean 名或数据库连接池名',
    thread_name     VARCHAR(128)    COMMENT '产生告警的线程名',
    trace_id        VARCHAR(64)     COMMENT '链路追踪 ID，取自 MDC traceId/X-B3-TraceId',
    fingerprint     VARCHAR(64)     NOT NULL COMMENT '告警指纹，用于去重与聚合，md5 32 位',
    repeat_count    INT             NOT NULL DEFAULT 1 COMMENT '去重窗口内重复次数',
    first_time      DATETIME(3)     NOT NULL COMMENT '去重窗口内首次发生时间',
    alert_time      DATETIME(3)     NOT NULL COMMENT '最近一次发生时间',
    create_time     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录落库时间，由写入线程在批量 INSERT 前统一填充，用于增量拉取',
    extra           TEXT            COMMENT '扩展信息，JSON 文本，最长 4096 字符',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微服务告警信息表，由 alert-starter 写入';

-- -----------------------------------------------------------------------------
-- 索引
-- -----------------------------------------------------------------------------
CREATE INDEX idx_alert_time  ON alert_info (alert_time);
CREATE INDEX idx_create_time ON alert_info (create_time);
CREATE INDEX idx_type_time   ON alert_info (alert_type, alert_time);
CREATE INDEX idx_fp_time     ON alert_info (fingerprint, alert_time);
CREATE INDEX idx_app_time    ON alert_info (app_name, alert_time);
CREATE INDEX idx_trace       ON alert_info (trace_id);

-- -----------------------------------------------------------------------------
-- 权限（按实际账号调整，示例；MySQL 不需要序列权限）
-- -----------------------------------------------------------------------------
-- GRANT INSERT ON alert_info TO '<应用账号>'@'%';
-- GRANT SELECT ON alert_info TO '<Zabbix 读取账号>'@'%';

-- -----------------------------------------------------------------------------
-- 回滚
-- -----------------------------------------------------------------------------
-- DROP TABLE alert_info;
