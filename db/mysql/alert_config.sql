-- =============================================================================
-- alert-starter 共享库配置表 DDL
-- 数据库 : MySQL（8.0 验证，5.7 兼容）
-- 执行方式: 与 db/mysql/alert_info.sql 在同一个项目组共享库执行
--           存量应用（只有 18 条配置）见文件末尾「存量应用补插」段，替换 <APP_NAME> 后执行
-- 版本   : v0.3-mysql
--
-- 部署模型:
--   一个项目组的 8 个微服务共用一个库、一个 database、一个账号；
--   alert_info 与 alert_config 同库；
--   alert.datasource.* 同时用于告警写入与配置加载；
--   其他项目组各自一套，同一库内通过 app_name 区分应用。
--
-- 权限:
--   应用账号（不引入管理页面）: INSERT alert_info、SELECT alert_config
--   应用账号（引入管理页面）  : INSERT alert_info、SELECT/INSERT/UPDATE alert_config
--   管理服务账号      : SELECT / INSERT / UPDATE alert_config
--   不建议授予 DELETE。
-- =============================================================================

CREATE TABLE alert_config (
    id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    app_name      VARCHAR(128)    NOT NULL COMMENT '应用名，与 spring.application.name 对齐',
    config_key    VARCHAR(128)    NOT NULL COMMENT '配置键，与 AlertProperties 对齐，去掉 alert. 前缀',
    config_value  TEXT            COMMENT '配置值，统一存字符串',
    value_type    VARCHAR(16)     NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/INT/LONG/DOUBLE/BOOL/LIST',
    config_group  VARCHAR(32)     COMMENT '页面分组：WRITE/DEDUP/RATE_LIMIT/BIZ/ERROR/SLOW_REQUEST/CONSOLE',
    description   VARCHAR(512)    COMMENT '配置说明',
    editable      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '页面是否允许编辑',
    version       BIGINT          NOT NULL DEFAULT 1 COMMENT '版本号，页面保存时 +1，用于乐观锁与刷新比对',
    update_by     VARCHAR(64)     COMMENT '最后修改人，本期可记录来源 IP 或 system',
    update_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
    create_time   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_alert_config UNIQUE (app_name, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警运行时配置，alert-starter 启动/刷新时加载';

CREATE INDEX idx_alert_config_app ON alert_config (app_name);

-- =============================================================================
-- 初始化配置（只提供一个服务的示例）
--
-- 1. 下面的 app_name 是示例，请替换为实际微服务名；
-- 2. 多个服务：复制整套 INSERT，替换 app_name 后再执行；
-- 3. 所有配置项都有默认值，后续可通过页面或 SQL 修改；
-- 4. console.instances 预置为空，页面首次打开后填写形如：
--      10.117.32.11:10009,10.117.32.12:10009
--    为空时，刷新按钮只刷新当前实例；
-- 5. 脚本不使用 ON CONFLICT，重复执行会因唯一约束报错；
--    如需重复执行，请先删除该应用的配置，或按数据库支持自行添加冲突处理。
-- =============================================================================

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'runtime.enabled', 'true', 'BOOL', 'CONSOLE', '运行时总开关');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'write-mode', 'ASYNC', 'STRING', 'WRITE', '写入模式：ASYNC/SYNC');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'queue.capacity', '1000', 'INT', 'WRITE', '有界队列容量（需重启生效）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'batch.size', '50', 'INT', 'WRITE', '单批最大条数');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'batch.interval-ms', '1000', 'LONG', 'WRITE', '批量等待间隔（毫秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'stack-trace.max-length', '8192', 'INT', 'ERROR', '堆栈最大字符数');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'dedup.window-seconds', '300', 'INT', 'DEDUP', '指纹去重窗口（秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'dedup.max-entries', '1000', 'INT', 'DEDUP', '去重指纹条目上限');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'rate-limit.per-minute', '20', 'INT', 'RATE_LIMIT', '每个告警类型每分钟上限');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'rate-limit.sample-factor', '10', 'INT', 'RATE_LIMIT', '超限后每 N 条放行 1 条');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'biz.enabled', 'true', 'BOOL', 'BIZ', '是否上报 AlertableException');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'error.npe.enabled', 'true', 'BOOL', 'ERROR', '是否上报 NullPointerException');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'error.npe.sample-rate', '1.0', 'DOUBLE', 'ERROR', 'NPE 采样率 0~1');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'error.stackoverflow.enabled', 'true', 'BOOL', 'ERROR', '是否上报 StackOverflowError');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-request.enabled', 'false', 'BOOL', 'SLOW_REQUEST', '是否采集慢请求');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-request.threshold-ms', '60000', 'LONG', 'SLOW_REQUEST', '慢请求阈值（毫秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-request.ignore-paths',
        '/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**',
        'LIST', 'SLOW_REQUEST', '慢请求忽略路径');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'async.enabled', 'false', 'BOOL', 'ASYNC', '是否采集 @Async 未捕获异常（默认关闭）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'async.args-types', 'true', 'BOOL', 'ASYNC', '是否记录异步方法参数类型列表（不记值）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-sql.enabled', 'false', 'BOOL', 'SLOW_SQL', '是否采集慢 SQL（默认关闭）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-sql.threshold-ms', '10000', 'LONG', 'SLOW_SQL', '慢 SQL 阈值（毫秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-sql.max-sql-length', '2048', 'INT', 'SLOW_SQL', 'SQL 文本最大字符数');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-sql.exclude-mapper-prefixes', '', 'LIST', 'SLOW_SQL', '排除的 mapper 前缀，逗号分隔');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'slow-sql.aggregate-window-seconds', '60', 'INT', 'SLOW_SQL', '同指纹聚合窗口（秒），0 表示不聚合');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'memory.enabled', 'false', 'BOOL', 'MEMORY', '是否采集内存/OOM 前兆（默认关闭）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'memory.interval-ms', '60000', 'LONG', 'MEMORY', '内存采样间隔（毫秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'memory.old-gen-ratio', '0.85', 'DOUBLE', 'MEMORY', '老年代 GC 后使用率阈值 0~1');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'memory.consecutive-samples', '3', 'INT', 'MEMORY', '连续采样次数：堆使用率持续上升且未发生 GC 时触发');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.enabled', 'false', 'BOOL', 'HTTP_OUTBOUND', '是否采集出站 HTTP 异常（默认关闭）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.threshold-ms', '60000', 'LONG', 'HTTP_OUTBOUND', '出站调用慢阈值（毫秒）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.slow-enabled', 'true', 'BOOL', 'HTTP_OUTBOUND', '是否采集成功但超过阈值的慢调用');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.statuses', '502,503,504', 'LIST', 'HTTP_OUTBOUND', '视为异常的下游状态码，逗号分隔');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.exclude-hosts', '', 'LIST', 'HTTP_OUTBOUND', '排除的主机，支持通配（如 *.internal.com）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'http.outbound.exclude-paths', '', 'LIST', 'HTTP_OUTBOUND', '排除的路径，支持 Ant 风格（如 /actuator/**）');

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
VALUES ('xk-report', 'console.instances', '', 'LIST', 'CONSOLE',
        '管理实例列表，格式 ip:port,ip:port；为空时只刷新当前实例');
-- =============================================================================
-- 权限（按实际账号调整，示例）
-- =============================================================================
-- 不引入管理页面
-- GRANT SELECT ON alert_config TO '<应用账号>'@'%';
-- 引入 alert-console-starter
-- GRANT SELECT, INSERT, UPDATE ON alert_config TO '<应用账号>'@'%';
-- GRANT SELECT, INSERT, UPDATE ON alert_config TO '<管理服务账号>'@'%';

-- =============================================================================
-- 回滚
-- =============================================================================
-- DROP TABLE alert_config;
