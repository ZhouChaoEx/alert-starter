# 新系统接入步骤（alert-starter + alert-console-starter）

面向**其他项目组**的操作手册：按本文顺序执行即可完成接入。每一步都给了可复制的配置、脚本和验收标准。

- 适用环境：Spring Boot **2.3.x**、Java **8**、Servlet Web 应用、KingbaseES（PostgreSQL 兼容模式）。
- 一个项目组共用一套库/schema/账号，`alert_info` 与 `alert_config` 同库，用 `app_name` 区分 8 个微服务。
- 只想采集告警就只做第 1~7 步；需要内嵌管理页面再加第 8 步。
- 细节参考：[`INTEGRATION.md`](./INTEGRATION.md)（行为边界、验证清单）、[`ALERT_CONSOLE_DESIGN.md`](./ALERT_CONSOLE_DESIGN.md)（设计）。

## 步骤总览

| 步骤 | 做什么 | 验收 |
| --- | --- | --- |
| 1 | 加 Maven 依赖 | `mvn dependency:tree` 能看到 `alert-starter` |
| 2 | DBA 执行建表 + 35 条初始化配置 | `alert_config` 有 35 行，`alert_info` 有 6 个索引 |
| 3 | 配置文件加数据源与开关 | 启动日志出现 `alert writer started` |
| 4 | 授权 | 应用账号能 INSERT `alert_info`、SELECT `alert_config` |
| 5 | 启动自检 | `/alert-console/api/health` 返回 success（接了页面时） |
| 6 | 业务代码使用告警 | 抛异常后表里有记录 |
| 7 | 打开管理页面填 `console.instances` | 刷新返回逐实例结果 |

> 第 2 步与第 3 步的先后可以互换，但**建议先建表再启动**：否则启动时 `alert table self-check` 会打 WARN（不影响启动，容易让人误以为没接好）。
> 第 3 步的配置里，`alert.datasource.url` 是必须有的，`alert.console.enabled=true` 是必须显式为 true 的。

---

## 0. 接入前确认

| # | 确认项 | 不符合时的后果 |
| --- | --- | --- |
| 1 | Spring Boot **2.x**（2.3.x 已验证；2.1~2.7 兼容） | 3.x 改用 `jakarta.servlet`，jar 直接引入会启动失败，需单独适配 |
| 2 | 业务侧有 Spring Boot BOM（parent 或 `spring-boot-dependencies`） | starter 的传递依赖不带版本，缺 BOM 会报 `dependencies.dependency.version is missing` |
| 3 | Java 8+（Boot 2.x 下 8/11/17 均可） | Boot 3 需要 17+，但本项目已不兼容 Boot 3 |
| 4 | 应用是 Servlet Web 应用 | 没有 Web 容器时页面、慢请求采集、`/alert/**` 端点都不生效 |
| 5 | 已有 Kingbase 驱动依赖（`kingbase8`） | 报 `ClassNotFoundException: com.kingbase8.Driver` |
| 6 | `spring.application.name` 已配置 | `app_name` 会变成 `unknown`，页面按应用名对不上 |
| 7 | 两个 starter 已在私服/本地仓库 | 依赖解析失败，需先由维护方 `mvn deploy` |

> **关于版本号**：starter 的 `spring-boot-starter-parent:2.3.7.RELEASE` 只是它自己的构建配置，
> **不会传递**给业务系统。发布到仓库的 pom 里 `spring-boot-starter` 等依赖不带版本，
> 运行时版本由业务系统的 Boot BOM 决定（xk-report 用 2.3.9 已验证）。
>
> 自查：
> `mvn dependency:tree | grep spring-boot` 看实际 Boot 版本；
> `mvn dependency:tree | grep -E "javax|jakarta"` 看 servlet 命名空间——`javax` 可直引，`jakarta` 不可。

---

## 1. 引入依赖

`alert-starter` 只有一个必选依赖；管理页面再加 `alert-console-starter`（它会传递 `alert-starter`）。

```xml
<!-- 必选：告警采集与写入 -->
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 可选：内嵌管理页面（配置编辑 + 大屏 + 告警列表） -->
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-console-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

starter **传递**的依赖：`spring-boot-starter-aop`（异常切面必需）。

以下依赖 starter **不传递**，由业务应用自己提供（通常已具备）：

| 依赖 | 用途 | 缺失后果 |
| --- | --- | --- |
| `spring-boot-starter-web` | 慢请求 Filter、Controller 切面、管理页面 | 慢请求与管理页面不生效 |
| `spring-boot-starter-jdbc` / `spring-jdbc` | `JdbcTemplate`（一般经 MyBatis 传递） | 不创建写入器与数据源 |
| `kingbase8` 驱动 | 连接告警库 | 启动报驱动类找不到 |

> `alert-starter` 刻意不传递驱动，避免与业务方版本冲突。

要求 `spring.aop.auto` 保持默认 `true`，否则 `@Aspect` 不生效（慢请求仍可用）。

---

## 2. 执行建表与初始化脚本

由 **DBA** 在项目组共享库执行，starter 不会自动建表。官方脚本：

| 脚本 | 作用 |
| --- | --- |
| [`db/alert_info.sql`](../db/alert_info.sql) | 告警表 + 6 个索引 |
| [`db/alert_config.sql`](../db/alert_config.sql) | 集中配置表 + 35 条初始化配置 |

```sql
-- 建议先切到本项目的 schema，表名固定不带 schema 前缀
SET search_path TO <你的_schema>;
```

### 2.1 建表语句

以下与 `db/*.sql` 一致（省略 `COMMENT ON` 语句，完整注释版以脚本文件为准）。

```sql
-- ------------------------- 告警表 -------------------------
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

-- ------------------------- 配置表 -------------------------
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

CREATE INDEX idx_alert_config_app ON alert_config (app_name);
```

### 2.2 初始化配置（INSERT）

`db/alert_config.sql` 里是 `app_name = 'xk-report'` 的示例。**把 `<APP_NAME>` 替换成自己的应用名**（必须与 `spring.application.name` 完全一致）后执行：

```sql
INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description) VALUES
('<APP_NAME>', 'runtime.enabled',              'true',    'BOOL',   'CONSOLE',      '运行时总开关'),
('<APP_NAME>', 'write-mode',                   'ASYNC',   'STRING', 'WRITE',        '写入模式：ASYNC/SYNC'),
('<APP_NAME>', 'queue.capacity',               '10000',   'INT',    'WRITE',        '有界队列容量（需重启生效）'),
('<APP_NAME>', 'batch.size',                   '50',      'INT',    'WRITE',        '单批最大条数'),
('<APP_NAME>', 'batch.interval-ms',            '1000',    'LONG',   'WRITE',        '批量等待间隔（毫秒）'),
('<APP_NAME>', 'stack-trace.max-length',       '8192',    'INT',    'ERROR',        '堆栈最大字符数'),
('<APP_NAME>', 'dedup.window-seconds',         '300',     'INT',    'DEDUP',        '指纹去重窗口（秒）'),
('<APP_NAME>', 'dedup.max-entries',            '10000',   'INT',    'DEDUP',        '去重指纹条目上限'),
('<APP_NAME>', 'rate-limit.per-minute',        '20',      'INT',    'RATE_LIMIT',   '每个告警类型每分钟上限'),
('<APP_NAME>', 'rate-limit.sample-factor',     '10',      'INT',    'RATE_LIMIT',   '超限后每 N 条放行 1 条'),
('<APP_NAME>', 'biz.enabled',                  'true',    'BOOL',   'BIZ',          '是否上报 AlertableException'),
('<APP_NAME>', 'error.npe.enabled',            'true',    'BOOL',   'ERROR',        '是否上报 NullPointerException'),
('<APP_NAME>', 'error.npe.sample-rate',        '1.0',     'DOUBLE', 'ERROR',        'NPE 采样率 0~1'),
('<APP_NAME>', 'error.stackoverflow.enabled',  'true',    'BOOL',   'ERROR',        '是否上报 StackOverflowError'),
('<APP_NAME>', 'slow-request.enabled',         'true',    'BOOL',   'SLOW_REQUEST', '是否采集慢请求'),
('<APP_NAME>', 'slow-request.threshold-ms',    '1000',    'LONG',   'SLOW_REQUEST', '慢请求阈值（毫秒）'),
('<APP_NAME>', 'slow-request.ignore-paths',    '/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**', 'LIST', 'SLOW_REQUEST', '慢请求忽略路径'),
('<APP_NAME>', 'async.enabled',                'false',   'BOOL',   'ASYNC',        '是否采集 @Async 未捕获异常（默认关闭）'),
('<APP_NAME>', 'async.args-types',             'true',    'BOOL',   'ASYNC',        '是否记录异步方法参数类型列表（不记值）'),
('<APP_NAME>', 'slow-sql.enabled',             'false',   'BOOL',   'SLOW_SQL',     '是否采集慢 SQL（默认关闭）'),
('<APP_NAME>', 'slow-sql.threshold-ms',        '500',     'LONG',   'SLOW_SQL',     '慢 SQL 阈值（毫秒）'),
('<APP_NAME>', 'slow-sql.max-sql-length',      '2048',    'INT',    'SLOW_SQL',     'SQL 文本最大字符数'),
('<APP_NAME>', 'slow-sql.exclude-mapper-prefixes', '',    'LIST',   'SLOW_SQL',     '排除的 mapper 前缀，逗号分隔'),
('<APP_NAME>', 'slow-sql.aggregate-window-seconds', '60', 'INT',    'SLOW_SQL',     '同指纹聚合窗口（秒），0 表示不聚合'),
('<APP_NAME>', 'memory.enabled',               'false',   'BOOL',   'MEMORY',       '是否采集内存/OOM 前兆（默认关闭）'),
('<APP_NAME>', 'memory.interval-ms',           '60000',   'LONG',   'MEMORY',       '内存采样间隔（毫秒）'),
('<APP_NAME>', 'memory.old-gen-ratio',         '0.85',    'DOUBLE', 'MEMORY',       '老年代 GC 后使用率阈值 0~1'),
('<APP_NAME>', 'memory.consecutive-samples',   '3',       'INT',    'MEMORY',       '连续采样次数：堆使用率持续上升且未发生 GC 时触发'),
('<APP_NAME>', 'http.outbound.enabled',        'false',   'BOOL',   'HTTP_OUTBOUND','是否采集出站 HTTP 异常（默认关闭）'),
('<APP_NAME>', 'http.outbound.threshold-ms',   '3000',    'LONG',   'HTTP_OUTBOUND','出站调用慢阈值（毫秒）'),
('<APP_NAME>', 'http.outbound.slow-enabled',   'true',    'BOOL',   'HTTP_OUTBOUND','是否采集成功但超过阈值的慢调用'),
('<APP_NAME>', 'http.outbound.statuses',       '502,503,504', 'LIST', 'HTTP_OUTBOUND','视为异常的下游状态码，逗号分隔'),
('<APP_NAME>', 'http.outbound.exclude-hosts',  '',        'LIST',   'HTTP_OUTBOUND','排除的主机，支持通配（如 *.internal.com）'),
('<APP_NAME>', 'http.outbound.exclude-paths',  '',        'LIST',   'HTTP_OUTBOUND','排除的路径，支持 Ant 风格（如 /actuator/**）'),
('<APP_NAME>', 'console.instances',            '',        'LIST',   'CONSOLE',      '管理实例列表，格式 ip:port,ip:port；为空时只刷新当前实例');
```

注意：

1. **35 条都要插**，否则页面显示“未初始化”；只插部分也能跑（未插入的项回落到代码默认值）。
2. `console.instances` 必须预置为空串（不是 NULL），首次打开管理页面后再填实例地址。
3. `async.enabled`、`slow-sql.enabled`、`memory.enabled`、`http.outbound.enabled` 默认都是 `false`；需要采集时在页面或 SQL 里改成 `true` 并刷新，无需重启。
4. 脚本没有 `ON CONFLICT`，重复执行会撞唯一约束 `uk_alert_config`。
5. 同一套库里再加应用时，复制这套 INSERT 换 `app_name` 即可；也可以在管理页面点“新增应用”自动复制 35 条。
6. **覆盖边界**：`http.outbound` 只采集 Spring 容器管理的 `RestTemplate` Bean 与 `@FeignClient` 接口；
   业务自建的客户端（`new RestTemplate()`、Hutool `HttpUtil`、OkHttp、Apache HttpClient）不在采集范围内。

### 2.2.1 存量应用升级（已经部署过的环境）

如果应用之前只初始化了 18 条（没有 `async.*` / `slow-sql.*` / `memory.*` / `http.outbound.*`），按 2.2 的清单补齐这 17 条：

- ASYNC：`async.enabled`、`async.args-types`
- SLOW_SQL：`slow-sql.enabled`、`slow-sql.threshold-ms`、`slow-sql.max-sql-length`、`slow-sql.exclude-mapper-prefixes`、`slow-sql.aggregate-window-seconds`
- MEMORY：`memory.enabled`、`memory.interval-ms`、`memory.old-gen-ratio`、`memory.consecutive-samples`
- HTTP_OUTBOUND：`http.outbound.enabled`、`http.outbound.threshold-ms`、`http.outbound.slow-enabled`、`http.outbound.statuses`、`http.outbound.exclude-hosts`、`http.outbound.exclude-paths`

从 2.2 的清单复制这些行，把 `<APP_NAME>` 替换成实际应用名后执行。
重复执行会因唯一约束 `uk_alert_config` 报错，执行前先确认该应用还没有这些键；
执行后每个应用应为 35 条：`SELECT app_name, COUNT(*) FROM alert_config GROUP BY app_name;`

### 2.3 授权

```sql
-- 采集写入（所有接入应用）
GRANT INSERT ON alert_info TO <应用账号>;
GRANT USAGE, SELECT ON SEQUENCE alert_info_id_seq TO <应用账号>;

-- 不接入管理页面：配置只读
GRANT SELECT ON alert_config TO <应用账号>;

-- 接入管理页面：配置需要保存/新增应用
GRANT SELECT, INSERT, UPDATE ON alert_config TO <应用账号>;
GRANT USAGE, SELECT ON SEQUENCE alert_config_id_seq TO <应用账号>;

-- 给 Zabbix 等监控读取账号（可选）
GRANT SELECT ON alert_info TO <读取账号>;
```

> 不建议授予 `DELETE`：本项目不做变更历史，删了无法追溯。

---

## 3. 配置文件

最小接入只需要 `alert.datasource.*` 三行 + 管理页面开关；其余按需调整。

```properties
# ---------- 应用标识（app_name 来源，必须与 alert_config.app_name 一致）----------
spring.application.name=<APP_NAME>
# 可选：显式指定 app_name，优先级高于 spring.application.name
#alert.app-name=<APP_NAME>
# 可选：environment 标识，缺省取 spring.profiles.active
#alert.env=prod

# ---------- 必配置项：告警总开关（默认已启用）----------
# 不配置 = 关闭；配 false 会整体关闭采集（需重启）
alert.enabled=true

# ---------- 必配置项：告警库（必填，否则不会创建写入器与管理页面）----------
alert.datasource.url=jdbc:kingbase8://<host>:<port>/<db>?currentSchema=<schema>
alert.datasource.username=<账号>
alert.datasource.password=<密码>
alert.datasource.driver-class-name=com.kingbase8.Driver
alert.datasource.hikari.maximum-pool-size=2
alert.datasource.hikari.minimum-idle=1
alert.datasource.hikari.connection-timeout-ms=3000
alert.datasource.self-check=true

# ---------- 必配置项：集中配置表加载（默认 true）----------
alert.config.enabled=true
alert.config.self-check=true

# ---------- 可选配置：管理页面需要引入依赖alert-console-starter（默认 false，必须显式开启）----------
alert.console.enabled=true

# ---------- 可选配置：采集开关和阈值（也可在 alert_config 表中维护，或在控制台维护）----------
alert.biz.enabled=true
alert.slow-request.enabled=true
alert.error.npe.enabled=true
alert.error.stackoverflow.enabled=true

# ---------- 可选配置：默认关闭：M2 新增两类采集，需显式改成 true（页面或表里改，免重启）----------
alert.async.enabled=false
alert.async.args-types=true
alert.slow-sql.enabled=false
alert.slow-sql.threshold-ms=500
alert.slow-sql.max-sql-length=2048
alert.slow-sql.exclude-mapper-prefixes=
alert.slow-sql.aggregate-window-seconds=60

# ---------- 可选配置：默认关闭：内存/OOM 前兆采集，需显式改成 true（页面或表里改，免重启）----------
alert.memory.enabled=false
alert.memory.interval-ms=60000
alert.memory.old-gen-ratio=0.85
alert.memory.consecutive-samples=3

# ---------- 可选配置：默认关闭：出站 HTTP 采集，需显式改成 true（页面或表里改，免重启）----------
alert.http.outbound.enabled=false
alert.http.outbound.threshold-ms=3000
alert.http.outbound.slow-enabled=true
alert.http.outbound.statuses=502,503,504
alert.http.outbound.exclude-hosts=
alert.http.outbound.exclude-paths=

alert.slow-request.threshold-ms=1000
alert.slow-request.ignore-paths=/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**
alert.error.npe.sample-rate=1.0
alert.dedup.window-seconds=300
alert.rate-limit.per-minute=20
alert.rate-limit.sample-factor=10
alert.write-mode=ASYNC
alert.queue.capacity=10000
alert.batch.size=50
alert.batch.interval-ms=1000
```

### 3.1 必须为 true / 必须有值的配置

| 配置 | 默认 | 要求 | 说明 |
| --- | --- | --- | --- |
| `alert.datasource.url` | 无 | **必须有值** | 没有它不装配数据源、`alertJdbcTemplate`、写入器，管理页面也不会装配 |
| `alert.console.enabled` | `false` | **必须显式 `true`** | 否则 `/alert-console/**` 全部 404 |
| `alert.enabled` | 不配即开启 | 保持 `true` 或不配 | 配 `false` 关闭整套采集自动装配 |
| `alert.config.enabled` | `true` | 保持 `true` | 为 `false` 时不读 `alert_config`，页面改了也不生效 |
| `alert.biz.enabled` | `true` | 保持 `true` | `AlertableException` 上报开关 |
| `alert.slow-request.enabled` | `true` | 保持 `true` | 慢请求采集开关 |
| `alert.error.npe.enabled` | `true` | 保持 `true` | NPE 采集开关 |
| `alert.error.stackoverflow.enabled` | `true` | 保持 `true` | 栈溢出采集开关 |
| `alert.async.enabled` | `false` | **需显式 `true`** | `@Async` 未捕获异常采集开关；默认关闭避免升级即产生告警 |
| `alert.slow-sql.enabled` | `false` | **需显式 `true`** | 慢 SQL 采集开关；需要类路径有 MyBatis，否则整体不装配 |
| `alert.memory.enabled` | `false` | **需显式 `true`** | 内存/OOM 前兆采集开关；只读 JDK MXBean，无额外依赖 |
| `alert.http.outbound.enabled` | `false` | **需显式 `true`** | 出站 HTTP 采集开关；覆盖容器管理的 RestTemplate 与 @FeignClient 接口 |

> 配置优先级：`alert_config` 表（启动时加载一次）> `application.properties` / Apollo > 代码默认值。
> 引入管理页面后，页面保存 + 刷新即可改表里的值，无需重启。

---

## 4. 启动验证

启动后按顺序检查：

| # | 检查 | 预期 |
| --- | --- | --- |
| 1 | 日志 | `alert writer started, queue capacity=10000, batch size=50, interval=1000ms` |
| 2 | 日志 | `alert config refreshed, app=<APP_NAME>, items=35, version=1` |
| 3 | 日志 | 无 `alert table self-check failed`（失败只 WARN，不阻断启动） |
| 4 | 表 | `SELECT COUNT(*) FROM alert_config WHERE app_name='<APP_NAME>';` → 18 |
| 5 | 接口（接入了管理页面） | `curl http://<host>:<port>/alert-console/api/health` → `{"success":true,...}` |
| 6 | 页面 | 浏览器打开 `http://<host>:<port>/alert-console/` 能看到三个页签 |

若第 5 步 404，按顺序排查：`alert.console.enabled=true` → `alert.datasource.url` 是否配置 → 应用是否 Servlet Web 应用 → 业务拦截器是否拦截了 `/alert-console/**`。

---

## 5. 业务代码怎么用告警

### 5.1 抛出可告警异常（最常用）

```java
import com.middleware.alert.AlertLevel;
import com.middleware.alert.exception.AlertableException;

// 简单写法，级别默认 ERROR
throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL");

// 指定级别与附加信息（extra 会被序列化为 JSON 存入 alert_info.extra）
Map<String, Object> extra = new HashMap<String, Object>();
extra.put("orderNo", orderNo);   // 调用方保证已脱敏

throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL", cause,
        AlertLevel.CRITICAL, extra);

// 主动上报需要手动拦截封装异常
@ControllerAdvice
public class BasicExceptionHandler {
    // 封装告警异常，各自应用按照各自的返回规范来封装
    @ExceptionHandler(AlertableException.class)
    public ResponseEntity<RespDTO<String>> handleWarnException(AlertableException e) {
        log.warn(String.format("AlertableException caught:%S,:%S", BasicErrorCodeEnum.FAILED.getCode(),e.getMessage()),e);
        if (StringUtils.isNotBlank(e.getMessage())) {
            return ResponseEntity.ok(RespDTO.failed(BasicErrorCodeEnum.FAILED.getCode(), e.getMessage()));
        }
        return ResponseEntity.ok(RespDTO.failed(BasicErrorCodeEnum.FAILED));
    }
}
```

无论业务方有没有 `@ControllerAdvice` 把异常吞成 200，切面都能采到。

### 5.2 手动上报（没有异常时）

```java
import com.middleware.alert.AlertReporter;
import com.middleware.alert.AlertType;
import com.middleware.alert.AlertLevel;

AlertReporter.report(AlertType.BIZ_EXCEPTION, AlertLevel.ERROR,
        "对账任务异常", "渠道 A 对账文件缺失", null);
```

### 5.3 临时关闭 / 开启采集

```java
AlertReporter.disable();
// ...
AlertReporter.enable();
```

也可配 `alert.enabled=false`（需重启，影响整个应用）。

### 5.4 自动采集的部分（无需写代码）

| 类型 | 触发条件 |
| --- | --- |
| `BIZ_EXCEPTION` | 抛出 `AlertableException` 及其子类 |
| `NULL_POINTER` | 未捕获的 `NullPointerException`（受采样率控制） |
| `STACK_OVERFLOW` | 未捕获的 `StackOverflowError` |
| `SLOW_REQUEST` | 请求耗时超过 `slow-request.threshold-ms` |

---

## 7. 管理页面（可选，需第 1 步引入 alert-console-starter）

### 7.1 打开

```properties
alert.console.enabled=true
```

访问 `http://<host>:<业务端口>/alert-console/`。

> `alert.console` 下目前只有 `enabled` 一个开关，没有其它可调项。

**业务应用若有登录/鉴权拦截器，必须排除 `/alert-console/**`**，否则页面与接口会被拦截成 401/403。

页面无登录、无操作审计，仅限内网使用。

### 7.2 三个页签能力

| 页签 | 能力 |
| --- | --- |
| 告警配置 | 服务下拉/侧栏、分组编辑、显示库中值与当前实例生效值、需重启/未初始化标记、保存、保存并刷新、只刷新该服务、逐实例刷新结果 |
| 告警大屏 | KPI、按级别堆叠趋势（1h/6h/24h/7d）、类型分布、服务 Top 10、实时告警流、健康矩阵、慢请求 Top URI |
| 告警列表 | 跨服务查询：服务多选、类型、级别、时间范围、关键字、分页、详情抽屉（堆栈/SQL/扩展信息） |

### 7.3 填写实例列表（多实例刷新）

`console.instances` 是 **`alert_config` 表里的配置项**，不是 `application.properties` 的键。两种填法：

- 页面：告警配置 → 控制台分组 → `console.instances` → 填 `10.117.32.11:10009,10.117.32.12:10009` → 保存；
- SQL：

```sql
UPDATE alert_config
SET config_value = '10.117.32.11:10009,10.117.32.12:10009', version = version + 1
WHERE app_name = '<APP_NAME>' AND config_key = 'console.instances';

INSERT INTO alert_config (app_name, config_key, config_value, value_type, config_group, description)
SELECT '<APP_NAME>', 'console.instances', '10.117.32.11:10009,10.117.32.12:10009', 'LIST', 'CONSOLE',
       '管理实例列表，格式 ip:port,ip:port；为空时只刷新当前实例'
WHERE NOT EXISTS (SELECT 1 FROM alert_config
                  WHERE app_name = '<APP_NAME>' AND config_key = 'console.instances');
```

行为：

- 当前实例直接调用 `AlertConfigLoader.refresh()`；
- 其他实例调用 `POST http://ip:port/alert/refresh/local`（由 alert-starter 提供，连接 1s、读 3s，单个失败不影响其他）；
- 为空时只刷新当前实例；
- 只访问表里声明的地址，不接受请求参数传入任意 URL；
- 业务拦截器需放行 `/alert/**`（刷新 + 读取生效值）。

> **顺序很重要**：改完 `console.instances` 先保存，再点刷新，刷新才会使用新列表。
> 页面显示“0 个实例”就是这一行还没填（不是运行时探测失败）。

---

## 8. 接入自检清单

| # | 操作 | 预期 |
| --- | --- | --- |
| 1 | 建表 + 授权 | 两表存在，`alert_info` 6 个索引，`alert_config` 唯一约束 |
| 2 | 插入 35 条初始化配置 | `SELECT COUNT(*) ... = 35` |
| 3 | 配 `alert.datasource.*` 启动 | 日志 `alert writer started` |
| 4 | 检查配置加载 | 日志 `alert config refreshed, items=35` |
| 5 | 抛 `AlertableException` 调接口 | 1~2 秒内表中有记录，字段完整 |
| 6 | 同一接口连调 5 次 | 窗口内仍 1 条，`repeat_count=1` |
| 7 | 阈值调 1 后访问任意接口 | 产生 `SLOW_REQUEST`，`cost_ms` 有值 |
| 8 | 把密码改错后重启 | 应用正常启动、业务不受影响，日志出现写入失败 |
| 9 | `alert.enabled=false` 重启 | 无 `alert-writer` 线程，表无新数据 |
| 10 | 打开管理页面 | 三个页签可用；`console.instances` 填完后刷新返回逐实例结果 |

第 8 步验证的是“告警库不可用不影响业务”，请在测试环境做。

---

## 9. 常见问题

**Q：管理页面 404**
A：按顺序检查 `alert.console.enabled=true` → `alert.datasource.url` 是否有值 → 应用是否为 Servlet Web 应用 → 拦截器是否排除 `/alert-console/**`。

**Q：页面里当前服务显示“0 个实例”**
A：正常现象，实例数来自 `alert_config.console.instances` 这一行，建表预置为空。按 7.3 填写即可。

**Q：页面提示“非当前应用，实例生效值不可见”**
A：`alert_config.app_name` 与 `spring.application.name`（或 `alert.app-name`）不一致。用 `SELECT DISTINCT app_name FROM alert_config;` 对照实际拼写。

**Q：启动报 `ClassNotFoundException: com.kingbase8.Driver`**
A：业务方未提供 Kingbase 驱动，starter 不传递。加驱动依赖或检查驱动版本。

**Q：接口抛异常但表里没有记录**
A：依次检查 `alert.enabled`、`alert.biz.enabled`、数据源连通性、`alert_info` 的 INSERT 与序列权限，以及日志中的 `alert batch write failed`。

**Q：同一异常只看到一条**
A：去重窗口默认 300 秒，属预期；窗口结束会补一条汇总记录。把 `alert.dedup.window-seconds` 设为 `0` 可关闭窗口。

**Q：页面改了配置但没生效**
A：`alert.config.enabled` 必须为 `true`；改完要点“保存并刷新”或“只刷新该服务”，只保存不刷新不会影响已运行实例。

**Q：多个项目组能不能共用一个库？**
A：不建议。页面服务列表是 `UNION alert_config.app_name / alert_info.app_name`，共库会让一个组看到另一个组的服务与告警。一个项目组一套库/schema/账号。

---
