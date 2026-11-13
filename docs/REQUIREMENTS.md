# alert-starter 需求说明书

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v1.0（M1 已实现） |
| 编写日期 | 2026-11-11 |
| 状态 | M1 + P1 已完成；M2 已完成 `ASYNC_EXCEPTION` / `SLOW_SQL`，其余两类待做；M3 已完成 `OOM_WARNING` / `HTTP_OUTBOUND`；115 个测试通过 |
| 目标制品 | `com.middleware.alert:alert-starter:1.0.0` |
| 运行基线 | Java 8 / Spring Boot 2.3.7.RELEASE / Spring Cloud Hoxton.SR9 |

---

## 1. 背景与目标

公司各微服务当前对异常的处置方式不统一：有的只打日志，有的被全局异常处理器吞掉后转成 HTTP 200，故障往往依赖人工发现。本 starter 的目标是**统一捕获异常并落库**，由 Zabbix 侧读取 `alert_info` 表完成通知。

starter 的职责边界只有两件事：

1. 采集；
2. 写入数据库 `alert_info` 表。

Zabbix 的读取方式、去重展示、通知渠道不在本 starter 范围内。

### 1.1 目标

- 业务方引入依赖并配置数据源后，**零代码改动**获得 10 类告警采集能力。
- 采集与写入**不影响业务**：不阻塞业务线程、不改变异常行为、不污染业务 Spring 容器。
- 提供稳定的表结构，支撑后续 Zabbix 接入与问题定位。

### 1.2 非目标（Out of Scope）

- Zabbix 读取逻辑、告警收敛/通知、SNMP/邮件/短信通道。
- 告警恢复（RECOVERY）与告警关闭语义。
- 历史数据归档与清理。
- 非 Web 应用（纯定时任务、批处理、MQ 消费者）的接入。
- 告警数据的前端展示与查询接口。
- 多租户、多语言。

---

## 2. 现状与约束

基于仓库内 `xk-report`、`xk-order2`、`xk-web-server`、`xk-common`、`xk-basic` 的代码勘察：

| 项 | 现状 | 对设计的影响 |
| --- | --- | --- |
| 技术栈 | Boot 2.3.7、Java 8、Cloud Hoxton.SR9、MyBatis-Plus 3.4.3.4 | starter 编译目标 1.8，自动装配用 `META-INF/spring.factories` |
| 数据库 | KingbaseES（8.6 / 9.0）或 MySQL（5.7 / 8.0）；业务 SQL 全 ANSI 兼容 | DDL 分两套：`db/kingbase`（PG 语法）与 `db/mysql`（`AUTO_INCREMENT` / `TEXT` / `DATETIME(3)`） |
| 配置中心 | Apollo，`apollo.bootstrap.enabled=true` | 所有 `alert.*` 配置可由 Apollo 下发，代码内提供默认值 |
| 密码 | 形如 `ENC(...)`，由 `xk-basic` 的 `ConfigEnvironmentPostProcessor` 全局解密 | `alert.datasource.password` 可直接使用 `ENC()`，starter 不重复实现解密 |
| 全局异常处理 | 各服务普遍存在 `@ExceptionHandler(Exception.class)`，异常被转成 HTTP 200 + 业务错误码 | **不能依赖 HTTP 状态码判断业务异常**，必须在 Controller 层 AOP 捕获 `Throwable` |
| MyBatis 工厂 | `xk-report` 手工 `new SqlSessionFactoryBean` 并显式 `setPlugins(...)` | 仅注册 `Interceptor` Bean 不会生效，必须用 `BeanPostProcessor` 向所有 `SqlSessionFactory` 追加插件 |
| 线程池 | 多为原生 `ThreadPoolExecutor` + `CallerRunsPolicy` | 饱和时不会拒绝，`rejectedCount` 恒为 0；判定需用队列占用率与活跃线程数 |
| 链路追踪 | Sleuth 已引入 | `trace_id` 从 MDC（`traceId`/`X-B3-TraceId`）读取 |
| 数据库账号 | 业务账号多为只读或受限 | `alert_info` 建表与授权由 DBA 完成，应用账号只需 `INSERT` |

### 2.1 工程前置改造（编码阶段执行）

1. 移除 `spring-boot-maven-plugin`：starter 不能是可执行 jar，否则被依赖时类位于 `BOOT-INF/classes` 下会 `ClassNotFoundException`。
2. 删除 `src/main/resources/application.properties`：其中的 `spring.application.name=alert-stater` 会污染引入方（注册中心、链路追踪）。
3. 依赖策略：`spring-boot-starter-aop` 传递；`spring-boot-starter-web`、`spring-boot-starter-jdbc` 声明为 `optional`；`kingbase8` 与 `mysql-connector-java` 均以 `optional` 声明、不传递，业务方按告警库二选一声明，`driver-class-name` 留空时按 URL 推断。自动装配通过 `@ConditionalOnClass` 降级；启用告警（`alert.enabled=true`）时由 `AlertDatasourceValidationAutoConfiguration` 强制校验 `alert.datasource.url`、JdbcTemplate/Hikari 与驱动类，缺失直接启动失败，不再静默降级。
4. 包名统一为 `com.middleware.alert`。

---

## 3. 总体设计

### 3.1 分层

```
采集面                         统一模型              写入链路                    存储
─────────────────────────────────────────────────────────────────────────────────────
A 异常面   Controller AOP ─┐
          AsyncConfigurer ─┤
B 请求面   Servlet Filter ──┼─→ AlertEvent ─→ 指纹/限流 ─→ 有界队列 ─→ 批量写入 ─→ alert_info
C 出站面   Feign/RestTemplate┘                                │
D 资源面   采样任务 ────────┘                                 └─→ 失败降级
```

- **A 异常面**：`BIZ_EXCEPTION`、`ASYNC_EXCEPTION`、`STACK_OVERFLOW`、`NULL_POINTER`。
- **B 请求面**：`SLOW_REQUEST`。
- **C 出站面**：`HTTP_OUTBOUND`。
- **D 资源面**：`OOM_WARNING`、`SLOW_SQL`、`THREAD_POOL_SATURATED`、`CONNECTION_POOL_EXHAUSTED`。

### 3.2 线程模型

| 线程 | 数量 | 职责 |
| --- | --- | --- |
| 业务线程 | 0（不新建） | 采集点只做内存操作与入队 |
| `alert-writer-*` | 1 | 消费队列、批量 INSERT、失败重试 |
| `alert-monitor-*` | 1 | 定时采样内存、线程池（含 Tomcat 工作线程池）、连接池；慢 SQL 在业务线程内拦截，不依赖该线程 |

starter 常驻线程总数不超过 2，且全部为守护线程。

---

## 4. 告警类型规格

### 4.1 枚举

| 枚举值 | 中文名 | 级别（默认） | 里程碑 |
| --- | --- | --- | --- |
| `BIZ_EXCEPTION` | 业务异常 | ERROR | M1 |
| `SLOW_REQUEST` | 慢请求 | WARN | M1 |
| `STACK_OVERFLOW` | 栈溢出 | CRITICAL | M1 |
| `NULL_POINTER` | 空指针 | ERROR | M1 |
| `ASYNC_EXCEPTION` | 异步异常 | ERROR | M2 |
| `SLOW_SQL` | 慢 SQL | WARN | M2 |
| `THREAD_POOL_SATURATED` | 线程池饱和 | WARN | M2 |
| `CONNECTION_POOL_EXHAUSTED` | 连接池耗尽 | CRITICAL | M2 |
| `OOM_WARNING` | 内存溢出前兆 | CRITICAL | M3（已实现，默认关闭） |
| `HTTP_OUTBOUND` | 出站 HTTP 异常 | ERROR | M3（已实现，默认关闭） |

级别枚举：`INFO` / `WARN` / `ERROR` / `CRITICAL`。

### 4.2 BIZ_EXCEPTION（业务异常）

| 项 | 说明 |
| --- | --- |
| 触发点 | Controller 方法 AOP（`@Around`，匹配 `@RestController`、`@Controller` 的 public 方法），捕获 `Throwable` 后先上报再原样抛出 |
| 判定 | `instanceof AlertableException`（含子类） |
| 采集 | 异常类、message、堆栈、`request_uri`、`http_method`、`trace_id`、线程名、`extra` |
| 配置 | `alert.biz.enabled=true`、`alert.biz.stack-trace=true` |
| 约束 | 必须是主通道：AOP 先于 `@ControllerAdvice` 执行，业务方吞异常也不影响上报；通过 ThreadLocal 标记避免同一异常在多个采集面重复上报 |

`AlertableException` 规格：

```java
public class AlertableException extends RuntimeException {
    private final String code;        // 业务错误码
    private final AlertLevel level;   // 默认 ERROR
    private final Map<String, Object> extra; // 附加信息，序列化为 JSON，需脱敏
}
```

### 4.3 SLOW_REQUEST（慢请求）

| 项 | 说明 |
| --- | --- |
| 触发点 | `OncePerRequestFilter`，最高优先级 |
| 判定 | 请求耗时 > `alert.slow-request.threshold-ms`（默认 1000），且 URI 不在忽略列表 |
| 采集 | `request_uri`（优先取 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`，取不到则用原始 URI）、`http_method`、`http_status`、`cost_ms`、`trace_id` |
| 配置 | `alert.slow-request.enabled`、`threshold-ms`、`ignore-paths`、`aggregate-window-seconds` |
| 约束 | 异步请求（`request.isAsyncStarted()`）在 `AsyncListener.onComplete/onTimeout/onError` 时结算；去重聚合复用全局 `alert.dedup.window-seconds`，`aggregate-window-seconds` 在 M1 暂未启用；忽略 `/actuator/**`、`/health`、`/favicon.ico`、Swagger 相关路径 |

### 4.4 ASYNC_EXCEPTION（异步异常）

| 项 | 说明 |
| --- | --- |
| 触发点 | 注册 `AsyncConfigurer#getAsyncUncaughtExceptionHandler` |
| 判定 | 回调被调用即上报（仅 `void` 返回值的 `@Async` 方法会走此路径） |
| 采集 | 方法签名、参数类型列表、异常栈、`trace_id` |
| 配置 | `alert.async.enabled=false`（默认关闭，需显式开启）、`alert.async.args-types=true` |
| 约束 | 用 `@ConditionalOnMissingBean(AsyncConfigurer.class)`，业务方已自定义时不覆盖并打 WARN；**覆盖不到**：自建 `ThreadPoolExecutor` 直接 `submit/execute` 的任务、`@Scheduled` 任务。限制需写入接入文档 |

### 4.5 HTTP_OUTBOUND（出站 HTTP 异常）

| 项 | 说明 |
| --- | --- |
| 触发点 | Feign `Client` 装饰器 + `ErrorDecoder`；RestTemplate `ClientHttpRequestInterceptor` |
| 判定 | 连接超时/读超时；响应码 ∈ `alert.http.outbound.statuses`（默认 502,503,504）；耗时 > `threshold-ms`（默认 3000，独立开关） |
| 采集 | `target_url`（仅 scheme+host+path，**剔除 query**）、`http_method`、`http_status`、`cost_ms`、`trace_id` |
| 配置 | `alert.http.outbound.enabled`（默认关闭，需显式开启）、`threshold-ms`、`statuses`、`slow-enabled`、`exclude-hosts`、`exclude-paths` |
| 约束 | 默认拦截全部出站调用，通过 `exclude-hosts` 排除内部服务（支持通配，如 `*.spdbfl.com.cn`）；**不采集请求参数、请求体、响应体**；按 `target_url` + `http_status` 指纹聚合 |

> 明确不实现入站 5xx 告警。业务方 `@ExceptionHandler` 会把异常转成 200，入站状态码不具备诊断价值。

### 4.6 OOM_WARNING（内存溢出前兆）

| 项 | 说明 |
| --- | --- |
| 触发点 | `alert-monitor-*` 线程按 `interval-ms`（默认 60000）采样 |
| 判定 | 老年代 `MemoryPoolMXBean.getCollectionUsage().used / max > old-gen-ratio`（默认 0.85），**以 GC 后的使用量为准**；或连续 3 次采样堆使用率单调上升且 GC 后不回落 |
| 采集 | 各内存池 used/max/committed、GC 次数与累计耗时、活跃线程数、堆 used/max；写入 `extra` |
| 配置 | `alert.memory.enabled`（默认关闭，需显式开启）、`interval-ms`、`old-gen-ratio`、`consecutive-samples` |
| 约束 | 不使用 `@Scheduled`（避免依赖 `@EnableScheduling`），使用 starter 自建 `ScheduledExecutorService`；临近阈值时只写简短内容，不做大对象分配；同一指纹 5 分钟内只告警一次 |

### 4.7 SLOW_SQL（慢 SQL）

| 项 | 说明 |
| --- | --- |
| 触发点 | `BeanPostProcessor` 在所有 `SqlSessionFactory` 初始化后向其 `Configuration` 追加 MyBatis `Interceptor`，拦截 `Executor.query` / `Executor.update` |
| 判定 | 执行耗时 > `alert.slow-sql.threshold-ms`（默认 500） |
| 采集 | `mapper_id`、`sql_text`（参数保持 `?` 占位，**不拼接实参**，截断 2048 字符）、`cost_ms`、`trace_id` |
| 配置 | `alert.slow-sql.enabled`（默认关闭，需显式开启）、`threshold-ms`、`max-sql-length`、`exclude-mapper-prefixes`、`aggregate-window-seconds` |
| 约束 | 兼容 MyBatis 与 MyBatis-Plus；`@ConditionalOnClass(org.apache.ibatis.session.SqlSessionFactory.class)`；同一 `mapper_id` + SQL 指纹在窗口内聚合；starter 自身不通过 MyBatis 写库，无递归风险 |

### 4.8 STACK_OVERFLOW（栈溢出）

| 项 | 说明 |
| --- | --- |
| 触发点 | 异常面（Controller AOP 捕获 `Throwable`） |
| 判定 | `instanceof StackOverflowError` |
| 采集 | 异常类、message、堆栈前 20 帧、`request_uri`、`trace_id` |
| 配置 | `alert.error.stackoverflow.enabled=true`、默认级别 CRITICAL |
| 约束 | 此时线程栈已耗尽，catch 块内禁止字符串拼接、JSON 序列化等重操作，只记录固定结构；完整堆栈截断处理 |

### 4.9 NULL_POINTER（空指针）

| 项 | 说明 |
| --- | --- |
| 触发点 | 异常面 |
| 判定 | `instanceof NullPointerException` |
| 采集 | message（Java 8 无 Helpful NPE，需结合栈顶 5 帧定位）、堆栈、`request_uri`、`trace_id` |
| 配置 | `alert.error.npe.enabled=true`、`alert.error.npe.sample-rate`（默认 1.0，可按比例采样） |
| 约束 | **默认开启**，但必须做指纹去重 + 每分钟限流，否则生产环境必然洪泛 |

### 4.10 THREAD_POOL_SATURATED（线程池饱和）

| 项 | 说明 |
| --- | --- |
| 触发点 | `BeanPostProcessor` 收集所有 `Executor` 类型 Bean（含 `ThreadPoolTaskExecutor`、`ThreadPoolExecutor`），由采样线程周期性读取；Tomcat 工作线程池从 `TomcatWebServer` → `Connector` → `ProtocolHandler#getExecutor()` 获取 |
| 判定 | **有界队列的池**：队列占用率 `queue.size / queue.capacity ≥ queue-ratio`（默认 0.8），或 `activeCount == maxPoolSize` 连续 `consecutive-samples` 次（默认 3）<br>**Tomcat**：`busyThreads / maxThreads ≥ busy-ratio`（默认 0.9）连续 `consecutive-samples` 次，或 `connectionCount / maxConnections ≥ busy-ratio`<br>Tomcat 默认 `TaskQueue` 无界（capacity = `Integer.MAX_VALUE`），**队列占用率对它永远接近 0，不能复用该判据** |
| 采集 | `pool_name`（Bean 名或 `tomcat-<port>`）、core/max/active/queueSize/queueCapacity/rejectedCount/completedTaskCount、Tomcat 的 busyThreads/currentThreadsBusy/connectionCount、`trace_id` 置空 |
| 配置 | `alert.thread-pool.enabled`、`queue-ratio`、`busy-ratio`、`consecutive-samples`、`exclude-bean-names`、`include-tomcat=true` |
| 约束 | 不使用 `rejectedCount` 作为主要判据（业务多使用 `CallerRunsPolicy`，不会拒绝）；必须排除 starter 自身线程池；Tomcat 线程池默认纳入，取不到 `ProtocolHandler` 时打 WARN 跳过，不影响启动 |

### 4.11 CONNECTION_POOL_EXHAUSTED（连接池耗尽 / 获取连接超时）

| 项 | 说明 |
| --- | --- |
| 触发点 | ① 采样线程读取 `HikariPoolMXBean`；② 异常面匹配获取连接异常 |
| 判定 | `threadsAwaitingConnection > 0`；或 `activeConnections / maximumPoolSize ≥ active-ratio`（默认 0.9）；或异常类型/消息匹配 `SQLTransientConnectionException`、`CannotGetJdbcConnectionException`、`Connection is not available` |
| 采集 | `pool_name`、active/idle/total/waiting、等待线程数、异常栈、`trace_id` |
| 配置 | `alert.connection-pool.enabled`、`active-ratio`、`consecutive-samples`、`include-druid=false` |
| 约束 | 排除 starter 自身数据源；Druid 适配为可选（`@ConditionalOnClass(com.alibaba.druid.pool.DruidDataSource)`）；`pool_name` 无法获取时用“默认数据源” |

---

## 5. 事件模型与写入链路

### 5.1 AlertEvent 字段

| 字段 | 类型 | 必有 | 说明 |
| --- | --- | --- | --- |
| `appName` | String | 是 | 默认取 `spring.application.name` |
| `env` | String | 否 | 默认取 `spring.profiles.active`，无则 `default` |
| `instanceIp` | String | 否 | 首个非回环网卡 IPv4 |
| `alertType` | enum | 是 | 见 4.1 |
| `alertLevel` | enum | 是 | 见 4.1 |
| `title` | String | 是 | 简短标题，≤512 字符 |
| `content` | String | 否 | 可读描述 |
| `exceptionType` | String | 否 | 异常全限定类名 |
| `stackTrace` | String | 否 | 截断至 `stack-trace.max-length` |
| `requestUri` | String | 否 | 当前请求 URI 模板 |
| `targetUrl` | String | 否 | 出站目标 URL（去 query） |
| `httpMethod` | String | 否 | |
| `httpStatus` | Integer | 否 | |
| `costMs` | Long | 否 | |
| `sqlText` | String | 否 | |
| `mapperId` | String | 否 | |
| `poolName` | String | 否 | |
| `threadName` | String | 否 | |
| `traceId` | String | 否 | 取自 MDC `traceId`，兼容 `X-B3-TraceId` |
| `fingerprint` | String | 是 | 见 5.2 |
| `repeatCount` | int | 是 | 默认 1 |
| `firstTime` | timestamp | 是 | 窗口内首次发生时间 |
| `alertTime` | timestamp | 是 | 最近一次发生时间 |
| `createTime` | timestamp | 是 | 记录落库时间，由 writer 线程在批量 INSERT 前统一填充，供增量拉取使用 |
| `extra` | String | 否 | JSON，≤4096 字符 |

### 5.2 指纹与去重

```
fingerprint = md5(appName + "|" + alertType + "|" + 归一化特征)
```

归一化特征按类型：

| 类型 | 特征 |
| --- | --- |
| `BIZ_EXCEPTION` / `NULL_POINTER` / `STACK_OVERFLOW` / `ASYNC_EXCEPTION` | 异常类名 + 堆栈前 5 帧的方法签名 |
| `SLOW_REQUEST` | `requestUri`（URI 模板） |
| `SLOW_SQL` | `mapperId` + SQL 归一化（去空白、数字/字符串字面量替换为 `?`） |
| `HTTP_OUTBOUND` | `targetUrl` + `httpStatus` |
| 资源类 | `poolName` 或内存池名称 |

- 去重窗口 `alert.dedup.window-seconds`（默认 300）：
  - **首次出现立即入队落库**（`repeat_count=1`），避免告警延迟一个窗口；
  - 窗口内同指纹的后续事件只累加内存计数，不再入队；
  - 窗口到期后，若累计次数 > 1，补一条汇总事件（`repeat_count=N`，标题追加“窗口内共 N 次”）；
  - 因此一次持续故障通常产生 1 条首报 + 1 条窗口汇总，而不是只产生 1 条。
- 限流 `alert.rate-limit.per-minute`（默认 20，按 `alertType` 维度）：超限后每 `alert.rate-limit.sample-factor` 条放行 1 条，保留“仍在发生”的信号。
- M1 不实现 `ALERT_SELF` 元告警，丢弃量只通过 WARN 日志暴露。

### 5.3 写入链路

```
业务线程：构造 AlertEvent → 去重/限流 → offer(有界队列)  ──失败──→ 丢弃计数 + 日志
                                                              │
alert-writer 线程：poll 批量(最多 batch.size，最长等待 batch.interval-ms)
                    → JdbcTemplate.batchUpdate(INSERT)
                    → 成功：清理计数
                    → 失败：指数退避重试 3 次(100/500/2000ms)
                    → 仍失败：丢弃该批 + 计数 + WARN 日志（不落文件、不阻塞业务）
```

| 要求 | 说明 |
| --- | --- |
| 业务零阻塞 | `offer()` 非阻塞；队列满直接丢弃并计数，绝不 `put()` |
| 写入模式 | `alert.write-mode=async`（默认）；`sync` 在业务线程直接写库，失败按同样策略重试，建议仅用于关键告警 |
| 关闭 | `SmartLifecycle.stop()` 中停止接收、flush 队列，最长等待 5s |
| 写入失败 | 只重试、不持久化到文件；重试耗尽即丢弃该批并计数，通过 WARN 日志暴露；**告警丢失不可补偿** |
| 自愈 | 数据源恢复后，后续告警自动恢复写入；丢弃期间的告警不补发 |
| create_time | writer 线程在每次批量 INSERT 前统一取当前时间，同一批次值相同；去重聚合场景下它晚于 `first_time` 与 `alert_time` |
| 插入 SQL | `INSERT INTO alert_info(...) VALUES (...)`，列顺序固定，显式写入 `create_time`，不依赖表默认值 |

### 5.4 自监控

不提供 actuator 端点，也不接入 Micrometer。计数器已在各组件内维护，但 M1 尚未实现周期汇总输出，计划 M3 补齐：

- 队列积压量、入队成功/丢弃数；
- 写入成功/失败/重试批次；
- 各采集点内部异常次数（采集点自身异常必须吞掉）。

M1 可用的排查信息：`alert queue is full`、`alert batch write failed`、
`alert table self-check failed`、`alert writer stopped, written=..., dropped=..., failedBatches=...` 等 WARN/INFO 日志。

---

## 6. 数据模型

### 6.1 表

表名固定 `alert_info`，DDL 见 [`db/kingbase/alert_info.sql`](../db/kingbase/alert_info.sql) 与 [`db/mysql/alert_info.sql`](../db/mysql/alert_info.sql)。

- 由 DBA 手工执行，starter **不自动建表**。
- 应用账号只需 `INSERT` 权限；启动时做一次只读自检（`SELECT 1 FROM alert_info WHERE 1=0`），失败打 WARN 并降级，不阻断启动。
- 写端只 `INSERT`，不做 `UPDATE`（去重与冷却在应用内存完成），避免与 Zabbix 读取争锁。

### 6.2 字段与索引

| 索引 | 用途 |
| --- | --- |
| `idx_alert_time(alert_time)` | 按告警发生时间查询 |
| `idx_create_time(create_time)` | 按落库时间增量拉取（推荐 Zabbix 使用） |
| `idx_type_time(alert_type, alert_time)` | 按类型排查 |
| `idx_fp_time(fingerprint, alert_time)` | 指纹聚合查询 |
| `idx_app_time(app_name, alert_time)` | 按应用维度排查 |
| `idx_trace(trace_id)` | 按链路追踪定位 |

### 6.3 容量与保留

- 单服务预估：正常 < 100 条/天，故障场景受去重与限流约束 < 2000 条/小时。
- 数据保留与清理由 DBA 侧负责（建议保留 90 天），不在 starter 内实现。

---

## 7. 配置项

所有配置前缀 `alert.`，代码内提供默认值，可在 Apollo 或本地 `application.properties` 覆盖。

```properties
# ── 全局 ─────────────────────────────────────────────
alert.enabled=true
alert.app-name=                      # 默认 ${spring.application.name}
alert.env=                           # 默认 ${spring.profiles.active:default}
alert.write-mode=async               # async | sync
alert.stack-trace.max-length=8192

# ── 独立数据源（不与业务 DataSource 共用）────────────
alert.datasource.url=
alert.datasource.username=
alert.datasource.password=           # 支持公司 ENC()
# 可留空，按 URL 推断：com.kingbase8.Driver / com.mysql.cj.jdbc.Driver
alert.datasource.driver-class-name=
alert.datasource.hikari.maximum-pool-size=2
alert.datasource.hikari.minimum-idle=1
alert.datasource.hikari.connection-timeout-ms=3000
alert.datasource.self-check=true

# ── 管道 ─────────────────────────────────────────────
alert.queue.capacity=10000
alert.batch.size=50
alert.batch.interval-ms=1000
alert.dedup.window-seconds=300
alert.dedup.max-entries=10000
alert.rate-limit.per-minute=20
alert.rate-limit.sample-factor=10

# ── 业务异常 ─────────────────────────────────────────
alert.biz.enabled=true

# ── 慢请求 ───────────────────────────────────────────
alert.slow-request.enabled=true
alert.slow-request.threshold-ms=1000
alert.slow-request.ignore-paths=/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**
alert.slow-request.aggregate-window-seconds=60

# ── 异步异常 ─────────────────────────────────────────
alert.async.enabled=false        # 默认关闭，需显式开启
alert.async.args-types=true

# ── 出站 HTTP ────────────────────────────────────────
alert.http.outbound.enabled=false       # 默认关闭，需显式开启
alert.http.outbound.threshold-ms=3000
alert.http.outbound.slow-enabled=true
alert.http.outbound.statuses=502,503,504
alert.http.outbound.exclude-hosts=
alert.http.outbound.exclude-paths=

# ── 慢 SQL ───────────────────────────────────────────
alert.slow-sql.enabled=false     # 默认关闭，需显式开启
alert.slow-sql.threshold-ms=500
alert.slow-sql.max-sql-length=2048
alert.slow-sql.exclude-mapper-prefixes=
alert.slow-sql.aggregate-window-seconds=60

# ── 内存 ─────────────────────────────────────────────
alert.memory.enabled=false       # 默认关闭，需显式开启
alert.memory.interval-ms=60000
alert.memory.old-gen-ratio=0.85
alert.memory.consecutive-samples=3

# ── 线程池 ───────────────────────────────────────────
alert.thread-pool.enabled=true
alert.thread-pool.queue-ratio=0.8
alert.thread-pool.busy-ratio=0.9
alert.thread-pool.consecutive-samples=3
alert.thread-pool.exclude-bean-names=
alert.thread-pool.include-tomcat=true

# ── 连接池 ───────────────────────────────────────────
alert.connection-pool.enabled=true
alert.connection-pool.active-ratio=0.9
alert.connection-pool.consecutive-samples=3
alert.connection-pool.include-druid=false

# ── 错误类型 ─────────────────────────────────────────
alert.error.npe.enabled=true
alert.error.npe.sample-rate=1.0
alert.error.stackoverflow.enabled=true
```

配置变更要求：阈值类配置支持运行时刷新（`@ConfigurationProperties` + Apollo 自动更新），投递链路参数（队列容量、批量大小）在启动时生效。

---

## 8. 对外 API

```java
// 1. 业务异常：抛出即上报
throw new AlertableException("订单同步失败", "ORDER_SYNC_FAIL");

// 2. 手动上报（逃生口，供未覆盖场景使用）
AlertReporter.report(AlertType.BIZ_EXCEPTION, AlertLevel.ERROR, "标题", "内容", extra);

// 3. 采集开关（用于压测/批量任务临时关闭）
AlertReporter.disable();
AlertReporter.enable();
```

要求：`AlertReporter` 的所有方法**不得抛出异常**，内部捕获后仅打日志。

---

## 9. 自动装配与兼容性

- 使用 `META-INF/spring.factories` 注册 `AlertAutoConfiguration`（Boot 2.3 不支持 `AutoConfiguration.imports`）。
- 所有 Bean 使用 `@ConditionalOnMissingBean`，不覆盖业务自定义实现。
- 按需加载：
  - Web 相关：`@ConditionalOnClass(DispatcherServlet.class)` + `@ConditionalOnWebApplication(type = SERVLET)`；
  - MyBatis 相关：`@ConditionalOnClass(SqlSessionFactory.class)`；
  - Feign 相关：`@ConditionalOnClass(feign.Client.class)`；
  - RestTemplate 相关：`@ConditionalOnClass(RestTemplate.class)`，且仅在存在 `RestTemplate` Bean 时装饰。
- `BeanPostProcessor` 必须通过 `static @Bean` 方法注册，避免配置类过早实例化。
- 业务方不存在 `spring.application.name` 时，`appName` 回退为 `unknown` 并打 WARN。
- starter 不注册 `@ControllerAdvice`，不修改业务异常处理结果。
- 与公司 `xk-basic` 的 `ConfigEnvironmentPostProcessor` 兼容：读取密码时使用 `Environment.getProperty()`，解密后的值优先。

---

## 10. 非功能需求

| 维度 | 要求 |
| --- | --- |
| 性能 | 单次采集开销 < 1ms；10 类全开时业务 P99 增量 < 3% |
| 内存 | starter 常驻内存 < 20MB；去重窗口条目上限 10000，LRU 淘汰 |
| 线程 | 不超过 2 个，全部守护线程，命名 `alert-*` |
| 可用性 | 数据源不可用、表不存在、写失败均不阻断应用启动与业务请求 |
| 可观测 | M1 仅输出关键事件日志（队列满、写失败、自检失败、停机统计）；周期汇总指标在 M3 实现，不提供 actuator 端点 |
| 安全 | 不采集请求参数、请求体、响应体、SQL 实参；堆栈与 SQL 按长度截断；`extra` 由业务方保证脱敏 |
| 兼容 | 支持 Spring Boot 2.3.x / Java 8；不引入与业务冲突的传递依赖 |
| 可测试 | 采集点单测不依赖真实数据库，使用内存 `AlertSink` |

---

## 11. 验收标准

| # | 验收项 | 通过标准 |
| --- | --- | --- |
| 1 | 引入即生效 | 仅添加依赖 + 配置数据源，未写任何代码，10 类告警均可产生数据 |
| 2 | 业务异常 | 抛出 `AlertableException` 后表中出现 `BIZ_EXCEPTION`，且业务返回值/异常行为不变 |
| 3 | 慢请求 | 阈值内不产生数据；超阈值产生一条，含 URI、耗时、`trace_id` |
| 4 | 出站 HTTP | 下游返回 504 或超时产生 `HTTP_OUTBOUND`，`target_url` 不含 query |
| 5 | 慢 SQL | 超过阈值产生一条，`sql_text` 中参数为 `?`，无实参 |
| 6 | 资源类 | 人为压满线程池/连接池后 3 个采样周期内产生告警 |
| 7 | 去重 | 5 分钟内同一异常重复 100 次：首次立即入库 1 条（`repeat_count=1`），窗口到期补 1 条汇总（`repeat_count=100`），窗口内不产生第 3 条 |
| 8 | 故障隔离 | 关闭告警库后业务接口 P99 无明显变化；队列满或写入失败均只丢弃并计数，不落文件、不阻塞业务 |
| 9 | 启动兼容 | 无 Web 环境、无 MyBatis、无 Feign 的服务引入后均能正常启动 |
| 10 | 关闭开关 | `alert.enabled=false` 后不产生任何采集与线程 |

---

## 12. 里程碑

| 里程碑 | 内容 | 交付物 |
| --- | --- | --- |
| M0（本次） | 需求评审 | 本文档 + `db/kingbase/alert_info.sql` / `db/mysql/alert_info.sql` |
| M1（已完成） | 工程改造、事件模型、独立数据源、异步批量写入、DDL、`BIZ_EXCEPTION` / `STACK_OVERFLOW` / `NULL_POINTER` / `SLOW_REQUEST` | starter 骨架 + 31 个单测 + 4 个端到端测试 + `docs/INTEGRATION.md` 接入示例 |
| P1（已完成） | 集中配置表 `alert_config`、运行时快照、读取点改造、手动刷新 | 46 个测试通过；设计见 `docs/ALERT_CONSOLE_DESIGN.md` |
| M2（部分完成） | `ASYNC_EXCEPTION` / `SLOW_SQL` 已实现（默认关闭）；`THREAD_POOL_SATURATED` / `CONNECTION_POOL_EXHAUSTED` 待做 | 采集实现 + 单测；本次新增 30 个测试，合计 77 个通过 |
| M3（部分完成） | `OOM_WARNING` / `HTTP_OUTBOUND` 已实现（默认关闭）；自监控待做 | 本轮新增 30 个测试，合计 115 个通过 |
| M4 | 灰度接入 `xk-report`，观察一周 | 接入记录与基线数据 |

---

## 13. 已知风险与评审决议

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 业务方已自定义 `AsyncConfigurer` | 异步异常采集失效 | `@ConditionalOnMissingBean` + 启动 WARN，文档说明 |
| 业务方自建 `SqlSessionFactory` 且未暴露 | 慢 SQL 采集失效 | `BeanPostProcessor` 在 `afterPropertiesSet` 后追加插件；仍失败则 WARN |
| `ENC()` 解密依赖 `xk-basic` | 未引入该包的服务密码无法解密 | 文档说明：未引入时使用明文或自行解密 |
| 告警库与业务库共用实例 | 业务库故障时告警同时失效 | 建议使用独立实例或至少独立库；写失败由 WARN 日志计数暴露 |
| 写库失败或队列满 | 告警丢失且不可恢复 | 已确认不做文件兜底；重试 3 次后丢弃并计数，通过日志暴露 |
| 多实例时间不同步 | `alert_time` 乱序 | 依赖 NTP；按 `alert_time` 查询时容忍秒级偏差 |
| 告警洪泛 | 表膨胀、Zabbix 噪音 | 指纹去重 + 每分钟限流 + 采样，默认开启 |
| 表字段变更 | 已上线服务升级困难 | 字段只增不减，写入使用显式列名 |

**评审决议（2026-11-10 已确认）**：

1. `alert_info` 不做分区，单表 + 索引即可。
2. 默认阈值按本文档取值，各业务系统可在 Apollo 中自行调整（慢请求 1000ms、慢 SQL 500ms、出站 3000ms）。
3. Tomcat 工作线程池纳入 `THREAD_POOL_SATURATED`，默认开启。
4. 不提供 actuator 端点，不接入 Micrometer。
5. 不做本地文件兜底：只写数据库，写入失败重试后丢弃并计数。
6. `alert_info` 增加 `create_time` 字段，语义为落库时间，由写入线程填充，并建立 `idx_create_time` 索引供增量拉取。
7. 写入链路采用“有界队列 + 单线程批量消费”的异步模型：业务线程只构造事件并 `offer`，不执行任何数据库操作。
8. 去重策略落地为“首次立即入库 + 窗口到期汇总”：首报不等待窗口，持续故障最多产生首报与汇总两条记录。
9. 慢请求聚合在 M1 复用全局去重窗口，`aggregate-window-seconds` 保留但暂不生效；周期自监控日志在 M3 补齐。
