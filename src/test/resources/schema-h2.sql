-- 集成测试建表脚本：与 db/alert_info.sql 保持一致，去掉 Kingbase 专有的 COMMENT 语句。
DROP TABLE IF EXISTS alert_info;

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

CREATE INDEX idx_alert_time  ON alert_info (alert_time);
CREATE INDEX idx_create_time ON alert_info (create_time);
CREATE INDEX idx_type_time   ON alert_info (alert_type, alert_time);
CREATE INDEX idx_fp_time     ON alert_info (fingerprint, alert_time);
CREATE INDEX idx_app_time    ON alert_info (app_name, alert_time);
CREATE INDEX idx_trace       ON alert_info (trace_id);

-- 集中配置表：与 alert_info 同库，测试中用于验证动态配置加载
DROP TABLE IF EXISTS alert_config;

CREATE TABLE alert_config (
    id            bigserial       PRIMARY KEY,
    app_name      varchar(128)    NOT NULL,
    config_key    varchar(128)    NOT NULL,
    config_value  text,
    value_type    varchar(16)     NOT NULL DEFAULT 'STRING',
    config_group  varchar(32),
    description   varchar(512),
    editable      boolean         NOT NULL DEFAULT true,
    version       bigint          NOT NULL DEFAULT 1,
    update_by     varchar(64),
    update_time   timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time   timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_config UNIQUE (app_name, config_key)
);
