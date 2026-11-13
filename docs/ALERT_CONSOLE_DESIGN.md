# 告警集中配置与 Console 设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v1.1（P1 + P2 已实现） |
| 编写日期 | 2026-11-11 |
| 适用版本 | alert-starter 1.0.0 之后 |
| 关联文档 | `docs/REQUIREMENTS.md`、`docs/INTEGRATION.md` |

---

## 1. 背景与目标

当前告警参数全部走 Apollo。每个应用改阈值、开关都要进 Apollo 操作，业务方难自助，也无法在一个页面里横向对比多个应用。

本设计引入：

1. **共享库**：在项目组共享库中新增 `alert_config` 表（与 `alert_info` 同库），存放所有运行时可调参数；
2. **动态配置加载**：`alert-starter` 启动时从表加载，维护本地不可变快照；
3. **管理页面**：新增 `alert-console-starter`，业务应用引入后随应用启动，通过业务端口访问；
4. **刷新机制**：页面保存后按 `console.instances` 配置逐个实例刷新，不轮询。

### 1.1 本期目标

- Apollo 只保留启动引导配置（数据源、总开关等）；
- 开关、阈值、忽略路径、采样率等全部可在页面修改；
- 页面保存后点击刷新，配置在目标实例立即生效；
- 不引入 `alert-console-starter` 的应用保持现有行为，改配置后重启生效。

### 1.2 非目标（本期不做）

- 权限、登录、按应用授权（内网使用，先不做）；
- 变更历史查询与回滚（不做；仅保留 `update_by` / `update_time` 字段）；
- Zookeeper / 注册中心广播刷新；
- 告警大屏与告警内容分析；
- 独立管理端口（复用业务端口）；
- 配置灰度发布。

---

## 2. 总体架构

```
┌─────────────────────────────────────────────┐
│ 项目组共享库（alert.datasource）              │
│   库 / schema / 账号：8 个微服务共用          │
│   alert_info    告警数据                      │
│   alert_config  运行时配置                    │
└──────────────────┬──────────────────────────┘
                   │ 启动加载 / 手动刷新
┌──────────────────▼──────────────────────────┐
│ alert-starter                                │
│   AlertConfigRepository  读配置表             │
│   AlertConfigLoader      构建/替换快照        │
│   AlertRuntimeConfig     不可变快照           │
│   AlertConfigProvider    采集点读取入口        │
└──────────────────┬──────────────────────────┘
                   │ 采集点只读快照，不查库
                   │ 告警写入 alert_info

┌─────────────────────────────────────────────┐
│ alert-console-starter（可选）                 │
│   静态页面 /alert-console/                    │
│   REST API /alert-console/api/**              │
│   保存后调用 AlertConfigLoader.refresh()      │
│   按 console.instances 调用其他实例刷新接口     │
└─────────────────────────────────────────────┘
```

### 2.1 一个数据源

| 数据源 | 配置前缀 | 用途 | 是否必须 |
| --- | --- | --- | --- |
| 项目组共享库 | `alert.datasource.*` | 写入 `alert_info`，读取 `alert_config`；一个项目组 8 个微服务共用库/schema/账号，`app_name` 区分应用 | 是（写入与配置加载） |

- `alert_info` 和 `alert_config` 在同一个库、同一个 schema；
- 8 个微服务的 `alert.datasource.*` 指向同一个库；
- 连接信息放 Apollo，因为必须先于配置表加载；
- 不配置 `alert.datasource.url` 时，既不写告警也不加载表配置，使用 Apollo/默认值。

### 2.2 配置优先级

```
代码默认值  <  Apollo / 本地 application.properties  <  alert_config 表
```

例外（只允许 Apollo/本地配置，表不能覆盖）：

- `alert.enabled`
- `alert.datasource.*`
- `alert.config.enabled` / `alert.config.self-check`
- `alert.console.*`
- `alert.app-name` / `alert.env`（启动时确定）

---

## 3. 数据模型

### 3.1 alert_config

```sql
CREATE TABLE alert_config (
    id            bigserial PRIMARY KEY,
    app_name      varchar(128) NOT NULL,
    config_key    varchar(128) NOT NULL,
    config_value  text,
    value_type    varchar(16)  NOT NULL DEFAULT 'STRING',
    config_group  varchar(32),
    description   varchar(512),
    editable      boolean      NOT NULL DEFAULT true,
    version       bigint       NOT NULL DEFAULT 1,
    update_by     varchar(64),
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_config UNIQUE (app_name, config_key)
);
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `app_name` | 应用隔离，与 `spring.application.name` 对齐 |
| `config_key` | 与 `AlertProperties` 对齐，去掉 `alert.` 前缀 |
| `config_value` | 统一存字符串，按 `value_type` 解析 |
| `value_type` | `STRING` / `INT` / `LONG` / `DOUBLE` / `BOOL` / `LIST` |
| `config_group` | 页面分组：`WRITE` / `DEDUP` / `RATE_LIMIT` / `BIZ` / `ERROR` / `SLOW_REQUEST` / `CONSOLE` |
| `editable` | 页面是否允许编辑，预留只读项 |
| `version` | 乐观锁与增量刷新，页面每次保存 +1 |
| `update_by` | 本期记录来源 IP 或 `system`，不做用户级审计 |

### 3.2 不建的表

- `alert_app`：不采用。页面应用列表从 `alert_config.app_name` 去重。
- `alert_config_history`：不采用。仅保留 `alert_config.update_by` / `update_time` 作为轻量记录。

---

## 4. 可动态配置项

`config_key` 与 `AlertProperties` 的对应关系如下。未列出的项（数据源、appName、env）仍由 Apollo/默认值提供。

| config_key | group | type | 默认值 | 刷新后 |
| --- | --- | --- | --- | --- |
| `runtime.enabled` | CONSOLE | BOOL | `true` | 实时 |
| `write-mode` | WRITE | STRING | `ASYNC` | 实时 |
| `queue.capacity` | WRITE | INT | `10000` | **需重启** |
| `batch.size` | WRITE | INT | `50` | 实时 |
| `batch.interval-ms` | WRITE | LONG | `1000` | 实时 |
| `stack-trace.max-length` | ERROR | INT | `8192` | 实时 |
| `dedup.window-seconds` | DEDUP | INT | `300` | 实时 |
| `dedup.max-entries` | DEDUP | INT | `10000` | 实时 |
| `rate-limit.per-minute` | RATE_LIMIT | INT | `20` | 实时 |
| `rate-limit.sample-factor` | RATE_LIMIT | INT | `10` | 实时 |
| `biz.enabled` | BIZ | BOOL | `true` | 实时 |
| `error.npe.enabled` | ERROR | BOOL | `true` | 实时 |
| `error.npe.sample-rate` | ERROR | DOUBLE | `1.0` | 实时 |
| `error.stackoverflow.enabled` | ERROR | BOOL | `true` | 实时 |
| `slow-request.enabled` | SLOW_REQUEST | BOOL | `true` | 实时 |
| `slow-request.threshold-ms` | SLOW_REQUEST | LONG | `1000` | 实时 |
| `slow-request.ignore-paths` | SLOW_REQUEST | LIST | `/actuator/**,/health,/favicon.ico,/swagger-ui/**,/v2/api-docs,/webjars/**` | 实时 |
| `async.enabled` | ASYNC | BOOL | `false`（默认关闭，需显式开启） | 实时 |
| `async.args-types` | ASYNC | BOOL | `true` | 实时 |
| `slow-sql.enabled` | SLOW_SQL | BOOL | `false`（默认关闭，需显式开启） | 实时 |
| `slow-sql.threshold-ms` | SLOW_SQL | LONG | `500` | 实时 |
| `slow-sql.max-sql-length` | SLOW_SQL | INT | `2048` | 实时 |
| `slow-sql.exclude-mapper-prefixes` | SLOW_SQL | LIST | 空 | 实时 |
| `slow-sql.aggregate-window-seconds` | SLOW_SQL | INT | `60` | 实时 |
| `memory.enabled` | MEMORY | BOOL | `false`（默认关闭，需显式开启） | 实时 |
| `memory.interval-ms` | MEMORY | LONG | `60000` | 实时 |
| `memory.old-gen-ratio` | MEMORY | DOUBLE | `0.85` | 实时 |
| `memory.consecutive-samples` | MEMORY | INT | `3` | 实时 |
| `http.outbound.enabled` | HTTP_OUTBOUND | BOOL | `false`（默认关闭，需显式开启） | 实时 |
| `http.outbound.threshold-ms` | HTTP_OUTBOUND | LONG | `3000` | 实时 |
| `http.outbound.slow-enabled` | HTTP_OUTBOUND | BOOL | `true` | 实时 |
| `http.outbound.statuses` | HTTP_OUTBOUND | LIST | `502,503,504` | 实时 |
| `http.outbound.exclude-hosts` | HTTP_OUTBOUND | LIST | 空（支持 `*.internal.com`） | 实时 |
| `http.outbound.exclude-paths` | HTTP_OUTBOUND | LIST | 空（支持 `/actuator/**`） | 实时 |
| `console.instances` | CONSOLE | LIST | 空（建表脚本预置空值，页面填写） | 保存即时 |

说明：

- `runtime.enabled` 是新增的**运行时总开关**，表里改成 `false` 后停止采集和写入，但 `alert-writer` 线程保留。与 `alert.enabled` 区分：后者是启动开关，改了要重启。
- `queue.capacity` 放表里是为了集中展示，但队列在启动时创建，刷新后不生效，页面需标注“需重启”。
- `rate-limit.*` 已改为每次 `allow()` 读取快照，刷新后实时生效。
- `slow-request.aggregate-window-seconds` 当前未实现，本期不放入配置表。
- `slow-sql.aggregate-window-seconds` 本期只做展示：慢 SQL 的聚合复用全局 `dedup.window-seconds`（同一 `mapper_id` + SQL 指纹在全局去重窗口内聚合），该配置项暂未参与计算。
- `console.instances` 为空时，刷新按钮只刷新当前实例；建表脚本会为应用预置全部配置项的默认值，实例列表由页面首次打开后填写。

### 4.1 值解析规则

| type | 解析 | 校验 |
| --- | --- | --- |
| BOOL | `true/false`，忽略大小写 | 非法值回退旧值并 WARN |
| INT / LONG | `Integer.parseInt` / `Long.parseLong` | 必须 ≥ 0，阈值类必须 > 0 |
| DOUBLE | `Double.parseDouble` | 采样率必须在 `[0,1]` |
| LIST | 逗号分隔，trim，忽略空项 | 路径必须符合 Ant 风格 |
| STRING | 原样 | `write-mode` 只允许 `ASYNC`/`SYNC` |

单条配置非法时：

- 记录 WARN（app、key、原始值、原因）；
- 使用上一条有效值；首次加载时使用代码默认值；
- **不因为一条配置非法而整体拒绝加载**。

---

## 5. 配置加载与刷新

### 5.1 组件

| 组件 | 职责 |
| --- | --- |
| `AlertConfigRepository` | 用现有 `alertJdbcTemplate` 查询配置表 |
| `AlertConfigLoader` | 全量加载、解析、校验、构建 `AlertRuntimeConfig`，原子替换快照 |
| `AlertRuntimeConfig` | 不可变配置快照，采集点只读 |
| `AlertConfigProvider` | 静态/单例入口，返回当前快照；未启用时返回 `AlertProperties` 兜底 |
| `AlertConfigRefresher` | 供 console 调用，执行 reload 并返回变更摘要 |

### 5.2 启动流程

1. 若 `alert.config.enabled=false`，跳过，全部使用 `AlertProperties`（`alert.datasource.url` 在启用告警时必填，缺失启动失败）；
2. 若配置了，尝试加载 `WHERE app_name = ?` 全量配置；
3. 加载成功：构建快照，打印最终生效配置（key=value，来源=表/Apollo/默认值）；
4. 加载失败（库不可用、表不存在）：打 WARN，使用 `AlertProperties`，应用继续启动；
5. `alert-writer` 的启动不依赖配置加载成功。

### 5.3 刷新流程

```
POST /alert/refresh/local
  → AlertConfigLoader.refresh()
      → 查询全量配置
      → 解析 + 校验
      → 与旧快照做 diff
      → AtomicReference.set(newSnapshot)
      → 打印变更日志
  → 返回 { instance, success, version, changedKeys, message }
```

- 刷新幂等，可重复调用；
- 刷新失败时保留旧快照，返回失败原因；
- 变更日志包含 key、旧值、新值、来源实例。

> 该端点由 **alert-starter** 提供，所有接入采集的服务都具备；管理端只需通过
> `console.instances` 远程调用，不需要业务服务引入 `alert-console-starter`。

### 5.4 多实例刷新

`console.instances` 存在 `alert_config` 表，按应用隔离：

| config_key | config_value | value_type |
| --- | --- | --- |
| `console.instances` | `10.117.32.11:10009,10.117.32.12:10009` | LIST |

页面保存后：

1. 当前实例直接调用 `AlertConfigLoader.refresh()`；
2. 读取 `console.instances`；
3. 跳过当前实例，逐个 `POST http://ip:port/alert/refresh/local`（由 alert-starter 提供）；
4. 连接超时 1s、读超时 3s；
5. 汇总每个实例的结果，页面展示成功/失败/超时；
6. 单个实例失败不影响其他实例。

约束：

- 实例地址格式必须为 `ip:port` 或 `host:port`，端口范围 1~65535；
- **刷新接口只允许访问 `console.instances` 中声明的地址**，不接受请求参数传入任意 URL，避免 SSRF；
- 实例扩缩容需要同步维护 `console.instances`；
- 未引入 `alert-console-starter` 的实例没有刷新接口，只能重启生效，页面标记为“未启用管理端”。

---

## 6. alert-console-starter

### 6.1 模块结构

```
alert-console-starter/
├── pom.xml
└── src/main
    ├── java/com/middleware/alert/console
    │   ├── autoconfigure/AlertConsoleAutoConfiguration.java
    │   ├── controller/AlertConsoleController.java
    │   ├── service/AlertConfigService.java
    │   ├── service/AlertInstanceRefresher.java
    │   └── model/...
    └── resources
        ├── META-INF/spring.factories
        └── static/alert-console/        # Vue 构建产物
```

### 6.2 自动装配条件

```java
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "alert.console", name = "enabled",
        havingValue = "true", matchIfMissing = false)
```

- 默认关闭，需显式配置 `alert.console.enabled=true`；
- 复用业务应用端口，路径统一为 `/alert-console`；
- 不注册独立 Tomcat Connector；
- 静态资源位于 `classpath:/static/alert-console/`。

### 6.3 REST API

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/alert-console/api/apps` | 应用列表（`alert_config.app_name` 去重） |
| `POST` | `/alert-console/api/apps` | 新增应用（复制 35 条默认配置） |
| `GET` | `/alert-console/api/services` | 服务列表（版本、实例、告警数、状态） |
| `GET` | `/alert-console/api/config?appName=x` | 配置项、当前生效值、实例列表、版本 |
| `PUT` | `/alert-console/api/config` | 保存配置（校验 + 版本乐观锁） |
| `GET` | `/alert-console/api/alerts` | 告警分页查询（`appNames`/`type`/`level`/`range`/`keyword`/`page`/`size`） |
| `GET` | `/alert-console/api/alerts/{id}` | 告警详情（含堆栈、SQL、扩展信息） |
| `GET` | `/alert-console/api/dashboard?range=` | 大屏聚合（`range`：`1h`/`6h`/`24h`/`7d`） |
| `POST` | `/alert-console/api/refresh` | 刷新全部实例 |
| `GET` | `/alert-console/api/config/effective?appName=&instance=` | 查看指定实例的生效值（本机读快照，其他实例远程拉取） |
| `GET` | `/alert-console/api/health` | 当前实例配置加载状态 |

> 刷新与读取生效值的实际端点在 **alert-starter**：`POST /alert/refresh/local`、`GET /alert/effective`。
> 管理端（console）只负责聚合与远程调用，业务服务不需要引入 console；
> 业务拦截器需放行 `/alert/**`。

保存请求示例：

```json
{
  "appName": "xk-report",
  "version": 12,
  "items": [
    {"key": "slow-request.threshold-ms", "value": "2000", "version": 12},
    {"key": "biz.enabled", "value": "true", "version": 12}
  ]
}
```

刷新响应示例：

```json
{
  "appName": "xk-report",
  "total": 2,
  "success": 1,
  "failed": 1,
  "results": [
    {"instance": "10.117.32.11:10009", "success": true, "costMs": 12},
    {"instance": "10.117.32.12:10009", "success": false, "costMs": 3001, "message": "read timeout"}
  ]
}
```

### 6.4 页面

技术栈：Vue 3 + Element Plus，Vite 构建，产物输出到 `src/main/resources/static/alert-console/`。

页面结构（三个页签，数据均来自 `alert_config` / `alert_info`）：

1. 告警配置
   - 顶部：服务下拉 + 版本/实例/告警数摘要、保存、保存并刷新、只刷新该服务；
   - 左侧：服务列表（版本、实例数、告警数、正常/待关注）；
   - 右侧：分组 tab + 表单 + 当前实例生效值对照 + 未初始化/需重启/只读标记，修改项标“已修改”；
   - 下方：刷新结果（每个实例的成功/失败/耗时/原因）。
2. 告警大屏
   - KPI：总数、近 1 小时、严重/错误/警告、受影响服务；
   - 按级别堆叠的趋势（1h/6h/24h/7d）、类型分布、服务 Top 10、健康矩阵、慢请求 Top URI；
   - 实时告警流（最近 8 条，点击看详情）；自动刷新 30s/60s/关闭、全屏；
   - 资源水位为 M2/M3 预留，未接入时展示提示。
3. 告警列表
   - 筛选：服务多选、类型、级别、时间范围、关键字（标题/URI/traceId）；
   - 统计：总数与严重/错误/警告；分页表格 + 详情抽屉（堆栈/SQL/扩展信息）。

表单校验：

- 阈值必须为正整数；
- 采样率 `0~1`；
- 忽略路径必须是合法 Ant 模式；
- `console.instances` 每项必须是 `host:port`。

---

## 7. 改造清单

### 7.1 alert-starter

| 类 | 改造 |
| --- | --- |
| `AlertProperties` | 保留，作为 Apollo/默认值兜底 |
| 新增 `AlertConfigProperties` | `alert.config.enabled`、`alert.config.self-check`、`alert.refresh.*` |
| 新增 `AlertConfigRepository` | 查询 `alert_config` |
| 新增 `AlertConfigLoader` | 加载、解析、校验、替换快照 |
| 新增 `AlertRuntimeConfig` | 不可变快照 |
| 新增 `AlertConfigProvider` | 采集点读取入口 |
| `SlowRequestFilter` | 开关、阈值、忽略路径改读快照 |
| `AlertExceptionHandler` | 业务异常/NPE/栈溢出开关、采样率、堆栈长度改读快照 |
| `AlertPublisher` | `runtime.enabled`、`write-mode` 改读快照；队列容量保持启动值 |
| `AlertWriter` | `batch.size` / `batch.interval-ms` 改读快照 |
| `DedupRegistry` | 窗口、条目上限改读快照 |
| `RateLimiter` | 改为每次 `allow()` 读快照，支持热更新 |
| `AlertAutoConfiguration` | 装配新组件；共享库不可用时降级 |
| `AlertPersistenceAutoConfiguration` | 复用 `alertJdbcTemplate`（`AlertJdbcAccess` 内部持有连接池），不新增数据源；不向宿主暴露 `DataSource` / `JdbcTemplate` 类型 bean |

### 7.2 新增模块

- `alert-console-starter`：自动装配、Controller、Service、静态页面。

### 7.3 配置示例

```properties
# Apollo：启动引导配置（8 个微服务指向同一个项目组共享库）
alert.enabled=true
# Kingbase
alert.datasource.url=jdbc:kingbase8://.../pv?currentSchema=alert
# MySQL 示例
# alert.datasource.url=jdbc:mysql://host:3306/alert_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
alert.datasource.username=...
alert.datasource.password=ENC(....)
# 可留空，按 URL 推断：com.kingbase8.Driver / com.mysql.cj.jdbc.Driver
alert.datasource.driver-class-name=
alert.datasource.self-check=true

# 配置表加载（复用同一个数据源）
alert.config.enabled=true
alert.config.self-check=true

# 管理页面（仅需要的应用开启）
alert.console.enabled=true
```

---

## 8. 向后兼容

| 场景 | 行为 |
| --- | --- |
| `alert.config.enabled=false` | 不加载表，完全使用 `AlertProperties`；启用告警时 `alert.datasource.url` 必填，缺失启动失败 |
| 未引入 `alert-console-starter` | 无页面；`alert-starter` 启动时加载表，改配置后重启生效 |
| 共享库不可用（启动） | WARN，降级到 `AlertProperties`，应用正常启动 |
| 共享库不可用（运行中刷新） | 保留旧快照，返回失败原因 |
| `alert_config` 表不存在 | WARN，降级到 `AlertProperties`，告警写入不受影响 |
| 表中缺少某项 | 使用 `AlertProperties` / 代码默认值 |
| 表中值非法 | 该项回退旧值/默认值并 WARN，其他项正常 |
| `alert.console.enabled=false` | 页面与 API 不注册 |

---

## 9. 非功能与安全

| 维度 | 要求 |
| --- | --- |
| 性能 | 采集点只读内存快照，不查库；刷新一次全量查询，配置项预计 < 30 条 |
| 启动 | 共享库不可用不阻断启动 |
| 线程 | 不新增常驻线程；刷新在请求线程执行 |
| 一致性 | 多实例逐个刷新，允许秒级不一致 |
| 安全 | 默认关闭；仅内网；刷新接口只访问配置表中的地址；不提供任意 URL 代理 |
| 可观测 | 启动打印最终生效配置；刷新打印 diff；失败打印实例与原因 |
| 资源 | console 静态资源约 1~2MB，只在引入模块的应用中存在 |

### 9.1 与业务拦截器

管理页面复用业务端口，若业务注册了登录/鉴权拦截器，需要排除 `/alert-console/**`。本项目 `UserInfoInterceptor` 不阻断请求，无需调整；其他应用接入时需在接入文档中说明。

### 9.2 共享库连接数评估

8 个微服务共用一个库后，连接数需要重新核算。连接池是按实例独立的，不是服务间共享：

```
可用连接 = max_connections - superuser_reserved_connections
需求峰值 = Σ(每个服务的实例数 × (业务连接池 maximumPoolSize + alert 连接池 maximumPoolSize))
建议：需求峰值 ≤ 可用连接 × 70%
```

其余 30% 留给 DBA、监控、备份和突发流量。

**第一步：查数据库上限和当前使用**

```sql
SHOW max_connections;
SHOW superuser_reserved_connections;

-- 当前连接总数与状态
SELECT count(*) AS total,
       sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END) AS active,
       sum(CASE WHEN state = 'idle'   THEN 1 ELSE 0 END) AS idle
FROM pg_stat_activity;

-- 按用户、应用、库分布
SELECT usename, application_name, datname, state, count(*)
FROM pg_stat_activity
GROUP BY usename, application_name, datname, state
ORDER BY count(*) DESC;
```

若当前库使用 `sys_` 前缀视图，把 `pg_stat_activity` 换成 `sys_stat_activity`。

**第二步：按服务估算（本项目基线）**

当前共享库：`max_connections = 500`，`superuser_reserved_connections = 3`，可用连接 **497**；按 70% 使用率，可规划预算约 **347**。

| 场景 | 实例数 | 业务池 max | 告警池 max | 业务连接 | 告警连接 | DBA/监控 | 合计 | 使用率 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 8 服务 × 2 实例 | 16 | 10 | 2 | 160 | 32 | 20 | 212 | 42.7% |
| 8 服务 × 3 实例 | 24 | 10 | 2 | 240 | 48 | 20 | 308 | 61.9% |
| 8 服务 × 4 实例 | 32 | 10 | 2 | 320 | 64 | 20 | 404 | 81.3% |
| 8 服务 × 2 实例 | 16 | 20 | 2 | 320 | 32 | 20 | 372 | 74.9% |

结论：当前 500 的配置下，8 个服务各 2 个实例、告警池 2 是安全的。即使所有服务业务池都配 20，总连接 372，使用率 74.9%，仍低于 80% 警戒线。

**当前实际配置**：8 个服务 × 2 实例，部分服务业务池 20，其余未配置（Hikari 默认 10）。按配 20 的服务数量：

| 配 20 的服务数 | 业务连接 | 告警连接 | DBA/监控 | 合计 | 使用率 |
| --- | --- | --- | --- | --- | --- |
| 0 | 160 | 32 | 20 | 212 | 42.7% |
| 2 | 200 | 32 | 20 | 252 | 50.7% |
| 4 | 240 | 32 | 20 | 292 | 58.8% |
| 6 | 280 | 32 | 20 | 332 | 66.8% |
| 8 | 320 | 32 | 20 | 372 | 74.9% |

2 实例下，配 20 的服务不超过 6 个时使用率低于 70%；8 个全配 20 也能运行，但余量只剩约 25%。

**如果扩到 3 实例**：

| 配 20 的服务数 | 合计 | 使用率 |
| --- | --- | --- |
| 0 | 308 | 61.9% |
| 1 | 338 | 68.0% |
| 2 | 368 | 74.0% |
| 3 | 398 | 80.1% |
| 8 | 548 | 110% |

3 实例下，配 20 的服务建议不超过 1 个；否则需要把业务池降到 10~15，或调大 `max_connections`。

按 70% 预算反推每个实例的业务池上限：

| 实例数 | 告警连接（×2） | 留给业务 | 每实例业务池建议 |
| --- | --- | --- | --- |
| 16 | 32 | 295 | ≤ 18 |
| 24 | 48 | 279 | ≤ 11 |
| 32 | 64 | 263 | ≤ 8 |

（均已扣除约 20 个 DBA/监控/备份连接。）

如果扩到 40 实例（8 服务 × 5 实例），总需求约 500，正好打满，届时必须降池或调大 `max_connections`。

**第三步：降低告警连接占用**

- `alert.datasource.hikari.maximum-pool-size` 默认 2。写入是单线程批量 INSERT，配置刷新只是偶发查询，2 个连接足够。
- `minimum-idle` 保持 1，避免空闲连接长期占用。
- 给告警连接打标：Kingbase 可在 JDBC URL 追加 `ApplicationName=alert-starter`（`pg_stat_activity.application_name` 可见）；MySQL 可用 `connectionAttributes=program_name:alert-starter`（需驱动支持）。
- 监控连接使用率，超过 70% 告警。

**第四步：测试环境验证**

1. 8 个服务全部启动，确认每个实例的 Hikari 池初始化完成；
2. 压测业务接口，同时触发几次告警写入；
3. 观察 `pg_stat_activity` 峰值和 `state='idle'` 的比例；
4. 确认 `active` 不会持续打满，`idle` 不会无限增长；
5. 把峰值记录到接入文档，作为后续扩实例的上限依据。

> 注意：`max_connections` 是实例级参数，调整后需要重启数据库；不要只看平均值，按“所有实例同时打满”的最坏情况估算。

---

## 10. 测试计划

| 层次 | 内容 |
| --- | --- |
| 单元测试 | 配置解析、类型校验、非法值回退、列表解析、快照替换、diff |
| 集成测试 | H2 建 `alert_config`，启动后阈值生效；修改表后刷新，断言新阈值 |
| 降级测试 | 共享库不可用、`alert_config` 表不存在、单条配置非法 |
| API 测试 | MockMvc 验证 GET/PUT/refresh、版本冲突、参数校验 |
| 多实例测试 | MockWebServer 模拟两个实例，验证成功/失败/超时汇总 |
| 兼容测试 | 不配 `alert.datasource` 或 `alert.config.enabled=false` 时行为与 1.0.0 一致 |

---

## 11. 里程碑

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| P0 | 本文档与 DDL 评审 | - |
| P1（已完成） | 配置表、加载器、快照、读取点改造、刷新接口、单测 | 已完成 |
| P2（已完成） | `alert-console-starter`、REST API、Vue 页面、实例刷新 | 已完成 |
| P3 | 联调、xk-report 验证、接入文档 | 2~3 天 |
| P4（后续） | 权限、ZK 广播、大屏 | 另评估 |

---

## 12. 风险与评审结论

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| `console.instances` 未随扩缩容更新 | 部分实例不生效 | 刷新结果标红；文档要求发布时同步更新 |
| 实例间网络不通 | 刷新失败 | 页面展示失败实例；支持只刷新当前实例 |
| 共享库单点 | 告警写入与配置刷新同时不可用 | 本地快照 + Apollo/默认值兜底；库故障时告警也会丢失 |
| 三份配置来源 | 排查困难 | 启动打印最终配置与来源；页面显示生效值 |
| 无权限误操作 | 告警静默/风暴 | 表单范围校验、恢复默认、P3 加权限 |
| 刷新接口 SSRF | 内网扫描 | 只访问 `console.instances` 中的地址 |
| `queue.capacity` 等需重启项被误认为实时 | 预期偏差 | 页面标注“需重启”；刷新结果里区分 |
| 业务拦截器拦截 `/alert-console` | 页面 401/403 | 接入文档要求排除该路径 |

**评审结论（2026-11-11 已确认）**：

1. 项目组共享库：`alert_info` 与 `alert_config` 同库，一个项目组 8 个微服务共用一个库/schema/账号、共用 `alert.datasource`，`app_name` 区分应用；其他项目组各自一套。
2. 不建 `alert_app` 表，应用列表从 `alert_config.app_name` 去重。
3. 不做变更历史，仅保留 `update_by` / `update_time`。
4. 建表脚本为应用预置全部配置项默认值；`console.instances` 预置空值，由页面填写。
5. 不限制管理页面来源 IP，仅要求内网访问。
