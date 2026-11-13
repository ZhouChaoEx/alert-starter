-- =============================================================================
-- alert-starter 告警表 DDL
-- 数据库 : KingbaseES（KINGBASE_ES 兼容模式，PostgreSQL 语法）
-- 执行方式: 由 DBA 手工执行，starter 不自动建表
-- 版本   : v0.2
-- 说明   : 应用账号只需 INSERT 权限；Zabbix 侧读取账号需 SELECT 权限
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 建议：若各服务使用独立 schema，请先切换 schema 再执行，例如
--   SET search_path TO alert;
-- 表名固定为 alert_info，不带 schema 前缀。
-- -----------------------------------------------------------------------------

CREATE TABLE alert_info (
    id              bigserial       PRIMARY KEY,
    app_name        varchar(128)    NOT NULL,
    env             varchar(32),
    instance_ip     varchar(64),
    alert_type      varchar(32)     NOT NULL,
    alert_level     varchar(16)     NOT NULL,
    title           varchar(512),
    content         text,
    exception_type  varchar(256),
    stack_trace     text,
    request_uri     varchar(512),
    target_url      varchar(512),
    http_method     varchar(16),
    http_status     int,
    cost_ms         bigint,
    sql_text        text,
    mapper_id       varchar(256),
    pool_name       varchar(128),
    thread_name     varchar(128),
    trace_id        varchar(64),
    fingerprint     varchar(64)     NOT NULL,
    repeat_count    int             DEFAULT 1 NOT NULL,
    first_time      timestamp       NOT NULL,
    alert_time      timestamp       NOT NULL,
    create_time     timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    extra           text
);

COMMENT ON TABLE  alert_info                 IS '微服务告警信息表，由 alert-starter 写入';
COMMENT ON COLUMN alert_info.id              IS '自增主键';
COMMENT ON COLUMN alert_info.app_name        IS '应用名，取自 spring.application.name';
COMMENT ON COLUMN alert_info.env             IS '环境，取自 spring.profiles.active，缺省 default';
COMMENT ON COLUMN alert_info.instance_ip     IS '实例 IP';
COMMENT ON COLUMN alert_info.alert_type      IS '告警类型：BIZ_EXCEPTION/SLOW_REQUEST/ASYNC_EXCEPTION/HTTP_OUTBOUND/OOM_WARNING/SLOW_SQL/STACK_OVERFLOW/NULL_POINTER/THREAD_POOL_SATURATED/CONNECTION_POOL_EXHAUSTED';
COMMENT ON COLUMN alert_info.alert_level     IS '告警级别：INFO/WARN/ERROR/CRITICAL';
COMMENT ON COLUMN alert_info.title           IS '告警标题，最长 512 字符';
COMMENT ON COLUMN alert_info.content         IS '告警描述';
COMMENT ON COLUMN alert_info.exception_type  IS '异常全限定类名';
COMMENT ON COLUMN alert_info.stack_trace     IS '异常堆栈，按配置截断（默认 8192 字符）';
COMMENT ON COLUMN alert_info.request_uri     IS '触发告警的请求 URI 模板（不含参数值）';
COMMENT ON COLUMN alert_info.target_url      IS '出站调用目标 URL，仅 scheme+host+path，已剔除 query';
COMMENT ON COLUMN alert_info.http_method     IS 'HTTP 方法';
COMMENT ON COLUMN alert_info.http_status     IS 'HTTP 状态码（出站场景为下游返回码）';
COMMENT ON COLUMN alert_info.cost_ms         IS '耗时（毫秒），含义随告警类型而定';
COMMENT ON COLUMN alert_info.sql_text        IS '慢 SQL 语句，参数保持 ? 占位，按配置截断';
COMMENT ON COLUMN alert_info.mapper_id       IS 'MyBatis MappedStatement id';
COMMENT ON COLUMN alert_info.pool_name       IS '线程池 Bean 名或数据库连接池名';
COMMENT ON COLUMN alert_info.thread_name     IS '产生告警的线程名';
COMMENT ON COLUMN alert_info.trace_id        IS '链路追踪 ID，取自 MDC traceId/X-B3-TraceId';
COMMENT ON COLUMN alert_info.fingerprint     IS '告警指纹，用于去重与聚合，md5 32 位';
COMMENT ON COLUMN alert_info.repeat_count    IS '去重窗口内重复次数';
COMMENT ON COLUMN alert_info.first_time      IS '去重窗口内首次发生时间';
COMMENT ON COLUMN alert_info.alert_time      IS '最近一次发生时间';
COMMENT ON COLUMN alert_info.create_time     IS '记录落库时间，由写入线程在批量 INSERT 前统一填充，用于增量拉取';
COMMENT ON COLUMN alert_info.extra           IS '扩展信息，JSON 文本，最长 4096 字符';

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
-- 权限（按实际账号调整，示例）
-- -----------------------------------------------------------------------------
-- GRANT INSERT ON alert_info TO <应用账号>;
-- GRANT USAGE, SELECT ON SEQUENCE alert_info_id_seq TO <应用账号>;
-- GRANT SELECT ON alert_info TO <Zabbix 读取账号>;

-- -----------------------------------------------------------------------------
-- 回滚
-- -----------------------------------------------------------------------------
-- DROP TABLE alert_info;
