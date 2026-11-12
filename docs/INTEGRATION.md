# alert-starter 接入指南（M1）

适用于 Spring Boot 2.3.x / Java 8 的 Servlet Web 应用。

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 可选：需要内嵌管理页面时引入 -->
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-console-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

starter 已传递 `spring-boot-starter-aop`。以下依赖由使用方提供（业务应用通常已具备）：

| 依赖 | 说明 |
| --- | --- |
| `spring-boot-starter-web` | 慢请求 filter 与 Controller 切面 |
| `spring-jdbc` / `spring-boot-starter-jdbc` | 使用方经 MyBatis 传递即可 |
| `kingbase8` 驱动 | 业务已有，starter 不传递，避免版本冲突 |

要求 `spring.aop.auto` 保持默认 `true`，否则 `@Aspect` 不生效（慢请求仍可用）。

---

## 2. 执行建表脚本

由 DBA 在同一个项目组共享库执行两个脚本：

| 脚本 | 作用 |
| --- | --- |
| [`db/alert_info.sql`](../db/alert_info.sql) | 告警表 |
| [`db/alert_config.sql`](../db/alert_config.sql) | 集中配置表，含默认初始化配置 |

```sql
GRANT INSERT ON alert_info TO <应用账号>;
GRANT USAGE, SELECT ON SEQUENCE alert_info_id_seq TO <应用账号>;

-- 不引入管理页面：只读配置
GRANT SELECT ON alert_config TO <应用账号>;

-- 引入 alert-console-starter：页面需要保存和新增应用
GRANT SELECT, INSERT, UPDATE ON alert_config TO <应用账号>;
```

`alert_info` 和 `alert_config` 同库，8 个微服务共用一套 `alert.datasource`。
starter **不会自动建表**。启动时对两张表各做一次只读自检，失败只打 WARN，不影响应用启动。

---

## 3. 配置（Apollo 或本地 application.properties）

```properties
# 应用标识，不配则回退 spring.application.name
spring.application.name=xk-report

alert.enabled=true
alert.env=prod

# 告警库独立数据源，不复用业务数据源
alert.datasource.url=jdbc:kingbase8://10.117.32.106:54321/pv?currentSchema=alert
alert.datasource.username=alert_writer
alert.datasource.password=ENC(....)
alert.datasource.driver-class-name=com.kingbase8.Driver
alert.datasource.hikari.maximum-pool-size=2
alert.datasource.hikari.minimum-idle=1
alert.datasource.hikari.connection-timeout-ms=3000
alert.datasource.self-check=true

# 集中配置表（与 alert_info 同库），默认 true
alert.config.enabled=true
alert.config.self-check=true

# 可选：开启内嵌管理页面
alert.console.enabled=true

# 写入链路
alert.write-mode=async
alert.queue.capacity=10000
alert.batch.size=50
alert.batch.interval-ms=1000
alert.dedup.window-seconds=300
alert.rate-limit.per-minute=20
alert.rate-limit.sample-factor=10

# 业务异常
alert.biz.enabled=true

# 慢请求
alert.slow-request.enabled=true
alert.slow-request.threshold-ms=1000
alert.slow-request.ignore-paths=/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**

# NPE / 栈溢出
alert.error.npe.enabled=true
alert.error.npe.sample-rate=1.0
alert.error.stackoverflow.enabled=true

# 异步异常（默认关闭，需显式开启；开启后无需重启）
alert.async.enabled=false
alert.async.args-types=true

# 慢 SQL（默认关闭，需显式开启；类路径需有 MyBatis）
alert.slow-sql.enabled=false
alert.slow-sql.threshold-ms=500
alert.slow-sql.max-sql-length=2048
alert.slow-sql.exclude-mapper-prefixes=
alert.slow-sql.aggregate-window-seconds=60

# 内存 / OOM 前兆（默认关闭，需显式开启；只读 JDK MXBean）
alert.memory.enabled=false
alert.memory.interval-ms=60000
alert.memory.old-gen-ratio=0.85
alert.memory.consecutive-samples=3

# 出站 HTTP（默认关闭，需显式开启）
# 覆盖 Spring 管理的 RestTemplate Bean 与 @FeignClient 接口；
# 业务自建的客户端（new RestTemplate()、Hutool HttpUtil、OkHttp）不在采集范围内
alert.http.outbound.enabled=false
alert.http.outbound.threshold-ms=3000
alert.http.outbound.slow-enabled=true
alert.http.outbound.statuses=502,503,504
alert.http.outbound.exclude-hosts=
alert.http.outbound.exclude-paths=
```

> 密码可直接使用公司 `ENC(...)`，由 `xk-basic` 的 `ConfigEnvironmentPostProcessor`
> 统一解密；未引入该组件的服务请使用明文或自行解密。

所有配置都有代码默认值，最小接入只需配置 `alert.datasource.url/username/password`。

> P1 已支持从 `alert_config` 加载开关与阈值：启动时读取一次。
> 引入 `alert-console-starter` 并开启 `alert.console.enabled` 后，可以在页面保存并刷新，立即生效。

---

## 4. 使用

### 4.1 业务异常

```java
throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL");
```

带级别与附加信息：

```java
Map<String, Object> extra = new HashMap<String, Object>();
extra.put("orderNo", orderNo);   // 调用方保证已脱敏

throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL", cause,
        AlertLevel.CRITICAL, extra);
```

### 4.2 手动上报

```java
AlertReporter.report(AlertType.BIZ_EXCEPTION, AlertLevel.ERROR,
        "对账任务异常", "渠道 A 对账文件缺失", null);
```

### 4.3 临时关闭采集

```java
AlertReporter.disable();
// ...
AlertReporter.enable();
```

或配置 `alert.enabled=false`（需重启）。

### 4.4 管理页面（可选）

引入 `alert-console-starter` 并配置：

```properties
alert.console.enabled=true
```

访问：

```
http://host:业务端口/alert-console/
```

功能：

- 应用下拉、新增应用（自动复制 35 条默认配置）
- 配置分组编辑、保存、保存并刷新、只刷新当前实例
- 显示库中值、当前实例生效值、未初始化/需重启标记
- 刷新结果按实例展示成功/失败/耗时

多实例刷新：`console.instances` 是 **`alert_config` 表里的配置项**（不是 `application.properties` 的键），
在管理页面「告警配置 → 控制台」中填写并保存，或直接改表：

```sql
UPDATE alert_config SET config_value = '<ip:port>,<ip:port>', version = version + 1
WHERE app_name = '<APP_NAME>' AND config_key = 'console.instances';
```

示例值：`10.117.32.11:10009,10.117.32.12:10009`。

当前实例直接刷新；其他实例调用 `POST http://ip:port/alert/refresh/local`
（该端点由 **alert-starter** 提供，所有接入采集的服务都具备，无需引入 `alert-console-starter`）。

业务应用若有登录/鉴权拦截器，需要排除 `/alert/**` 与 `/alert-console/**`。

> **升级顺序**：先升级全部业务服务（alert-starter），最后升级管理端（alert-console-starter）。
> 新端点 `/alert/**` 在旧版 alert-starter 上不存在，顺序反了会出现刷新/查看生效值 404。

---

## 5. 验证

1. 调用一个抛出 `AlertableException` 的接口；
2. 等待 1~2 秒（异步批量写入）；
3. 查询：

```sql
SELECT alert_type, alert_level, title, request_uri, trace_id, repeat_count,
       first_time, alert_time, create_time
FROM alert_info
ORDER BY id DESC
LIMIT 10;
```

预期：

- 首次异常立即产生一条 `BIZ_EXCEPTION` 记录；
- 同一异常在 `dedup.window-seconds` 内重复发生不会重复入库；
- 窗口到期后若重复次数大于 1，会补一条标题含“窗口内共 N 次”的汇总记录。

慢请求验证：把 `alert.slow-request.threshold-ms` 调小（如 1），访问任意接口即可。

---

## 6. 行为边界

| 场景 | 行为 |
| --- | --- |
| 业务方已有 `@ControllerAdvice` 吞异常 | 不影响采集：切面在 handler 调用层先拿到异常 |
| 告警库不可用 | 重试 3 次后丢弃该批，只打 WARN；不影响业务 |
| 队列满 | 非阻塞丢弃并计数，不阻塞业务线程 |
| 应用优雅停机 | `@PreDestroy`/SmartLifecycle 停止时 flush 队列，最长等待 5 秒 |
| 进程被 `kill -9` | 队列内未消费事件丢失（已确认不做文件兜底） |
| 数据库无 `alert_info` 表 | 启动自检 WARN，写入持续失败并计数 |
| 未配置 `alert.datasource.url` | 不创建写入器，采集侧仍工作但只入内存队列 |
| `alert.config.enabled=false` | 不加载 `alert_config`，使用 Apollo/代码默认值 |
| `alert_config` 表不存在或不可读 | 启动 WARN，降级 Apollo/默认值，告警写入不受影响 |

---

## 7. 常见问题

**Q：启动报 `ClassNotFoundException: com.kingbase8.Driver`**
A：业务方未提供 kingbase 驱动。starter 刻意不传递驱动，避免与业务版本冲突。

**Q：接口抛异常但表里没有记录**
A：依次检查 `alert.enabled`、`alert.biz.enabled`、数据源连通性、`alert_info` 表与 INSERT 权限，
以及日志中是否出现 `alert batch write failed` / `alert table self-check failed`。

**Q：同一异常只看到一条记录**
A：这是预期行为。去重窗口默认 300 秒，窗口结束会补一条重复次数汇总；如需每次记录，
将 `alert.dedup.window-seconds` 设为 0 会关闭窗口（首报仍走正常链路）。

**Q：NPE 太多**
A：降低 `alert.error.npe.sample-rate`（如 0.1）或关闭 `alert.error.npe.enabled`。

---

## 8. 验证方式

### 8.1 本地 / CI 端到端验证

```bash
mvn test -Dtest=AlertEndToEndTest
```

该用例启动一个最小 Web 应用（随机端口），通过真实 HTTP 请求验证：

| 场景 | 断言 |
| --- | --- |
| 业务异常被 `@ControllerAdvice` 转成 200 | 仍写入 `BIZ_EXCEPTION`，字段完整 |
| NPE | 写入 `NULL_POINTER` |
| 慢请求 | 写入 `SLOW_REQUEST`，`cost_ms` 正确 |
| 同一异常重复调用 | 窗口内只入库 1 条 |

覆盖范围：自动装配、Controller AOP、慢请求 Filter、异步批量写入、字段映射、去重。

**不覆盖**：Kingbase 驱动与方言、Apollo 配置下发、Sleuth 真实 traceId。这三项需在内网真实环境验证。

### 8.2 内网真实环境自检清单

按顺序执行，每步都有明确预期结果。

| # | 操作 | 预期 |
| --- | --- | --- |
| 1 | DBA 执行 `db/alert_info.sql`，授予 `INSERT` 与序列权限 | 表与 6 个索引存在 |
| 2 | Apollo 配置 `alert.datasource.*` | 启动日志出现 `alert writer started` |
| 3 | 启动 xk-report | 日志出现 `alert table self-check passed`（失败会 WARN 但不阻断） |
| 4 | 调用一个抛 `AlertableException` 的接口 | 1~2 秒内表中有 `BIZ_EXCEPTION`，`alert_level` / `title` / `request_uri` / `create_time` 正确 |
| 5 | 同一接口连续调用 5 次 | 窗口内仍只有 1 条，`repeat_count=1` |
| 6 | 临时把 `alert.slow-request.threshold-ms` 调为 1，访问任意接口 | 产生 `SLOW_REQUEST`，`cost_ms` 有值 |
| 7 | 用日志中的 traceId 查表 | `trace_id` 与 Sleuth 链路一致 |
| 8 | 把 `alert.datasource.password` 改错后重启 | 应用正常启动、业务接口正常，日志出现 `alert batch write failed` 或 `alert table self-check failed` |
| 9 | `alert.enabled=false` 重启 | 无 `alert-writer` 线程，表无新数据 |
| 10 | 100 并发调用抛业务异常的接口 | 业务 P99 无明显变化，日志无持续的 `alert queue is full` |

第 8 步是故障隔离验证，务必在测试环境执行，它验证“告警库不可用不影响业务”这一核心承诺。
