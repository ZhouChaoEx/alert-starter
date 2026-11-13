-- 集成测试建表脚本：H2 MySQL 模式，与 db/mysql/*.sql 保持一致（去掉 COMMENT/ENGINE）
DROP TABLE IF EXISTS alert_info;

CREATE TABLE alert_info (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_name        VARCHAR(128)    NOT NULL,
    env             VARCHAR(32),
    instance_ip     VARCHAR(64),
    alert_type      VARCHAR(32)     NOT NULL,
    alert_level     VARCHAR(16)     NOT NULL,
    title           VARCHAR(512),
    content         TEXT,
    exception_type  VARCHAR(256),
    stack_trace     TEXT,
    request_uri     VARCHAR(512),
    target_url      VARCHAR(512),
    http_method     VARCHAR(16),
    http_status     INT,
    cost_ms         BIGINT,
    sql_text        TEXT,
    mapper_id       VARCHAR(256),
    pool_name       VARCHAR(128),
    thread_name     VARCHAR(128),
    trace_id        VARCHAR(64),
    fingerprint     VARCHAR(64)     NOT NULL,
    repeat_count    INT             DEFAULT 1 NOT NULL,
    first_time      TIMESTAMP       NOT NULL,
    alert_time      TIMESTAMP       NOT NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    extra           TEXT
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
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_name      VARCHAR(128)    NOT NULL,
    config_key    VARCHAR(128)    NOT NULL,
    config_value  TEXT,
    value_type    VARCHAR(16)     NOT NULL DEFAULT 'STRING',
    config_group  VARCHAR(32),
    description   VARCHAR(512),
    editable      TINYINT(1)      NOT NULL DEFAULT 1,
    version       BIGINT          NOT NULL DEFAULT 1,
    update_by     VARCHAR(64),
    update_time   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_config UNIQUE (app_name, config_key)
);
