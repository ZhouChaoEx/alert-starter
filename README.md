# alert-starter

微服务告警采集 starter：捕获异常并写入 Kingbase / MySQL 的 `alert_info` 表，由 Zabbix 侧读取后发出通知。
支持从与 `alert_info` 同库的 `alert_config` 表加载运行时可调参数。

- 运行基线：Java 8 / Spring Boot 2.3.7.RELEASE
- 坐标：`com.middleware.alert:alert-starter:1.0.2`
- 原则：采集与写库不影响业务；数据源独立；写失败只丢弃并计数，不落文件

## 当前状态：M1 + P1 + P2 已完成

已实现的告警类型：

| 告警类型 | 采集方式 |
| --- | --- |
| `BIZ_EXCEPTION` | Controller AOP 捕获 `AlertableException` |
| `STACK_OVERFLOW` | 同一 AOP 捕获 `StackOverflowError` |
| `NULL_POINTER` | 同一 AOP 捕获 `NullPointerException`（可采样、默认开启） |
| `SLOW_REQUEST` | `OncePerRequestFilter`，含异步请求结算 |

基础设施：

- 独立 Hikari 数据源，不复用业务 `DataSource`
- 有界队列 + 单线程批量 INSERT，失败重试 3 次后丢弃
- 指纹去重（首报立即入库 + 窗口到期汇总）+ 按类型限流
- 集中配置表 `alert_config`：启动加载、本地不可变快照、手动刷新，库不可用时降级到 Apollo/默认值
- DDL 由 DBA 手工执行，starter 只做只读自检
- alert-starter 133 个测试（含 PostgreSQL / MySQL 双模式端到端）；配套 alert-console-starter 23 个测试（14 个集成 + 9 个单元）

P2 已完成：`alert-console-starter`（内嵌管理页面 + 配置 CRUD + 多实例刷新）。

后续里程碑：

- M2：`ASYNC_EXCEPTION`、`SLOW_SQL`、`THREAD_POOL_SATURATED`、`CONNECTION_POOL_EXHAUSTED`
- M3：`OOM_WARNING`、`HTTP_OUTBOUND`、周期自监控

## 快速开始

```xml
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-starter</artifactId>
    <version>1.0.2</version>
</dependency>

<!-- 可选：内嵌管理页面 -->
<dependency>
    <groupId>com.middleware.alert</groupId>
    <artifactId>alert-console-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```

```properties
alert.enabled=true

# 告警库二选一：Kingbase 或 MySQL；驱动类名留空时按 URL 推断
alert.datasource.url=jdbc:kingbase8://host:54321/pv?currentSchema=alert
# alert.datasource.url=jdbc:mysql://host:3306/alert_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
alert.datasource.username=alert_writer
alert.datasource.password=ENC(....)
# 可选；留空按 URL 推断：com.kingbase8.Driver / com.mysql.cj.jdbc.Driver
# alert.datasource.driver-class-name=

# 可选：启用 alert_config 表配置，默认 true
alert.config.enabled=true

# 可选：开启管理页面，默认 false
alert.console.enabled=true
```

完整步骤见 [接入指南](docs/INTEGRATION.md)。

## 文档

| 文件 | 说明 |
| --- | --- |
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | **新系统接入步骤**（依赖、建表脚本、配置、验证，可照着操作） |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 需求说明书（10 类告警、配置项、验收标准、里程碑） |
| [docs/ALERT_CONSOLE_DESIGN.md](docs/ALERT_CONSOLE_DESIGN.md) | 集中配置与 Console 设计（P1 + P2 已实现） |
| [docs/INTEGRATION.md](docs/INTEGRATION.md) | 接入指南（依赖、配置、验证、常见问题） |
| [db/kingbase/alert_info.sql](db/kingbase/alert_info.sql) | 告警表脚本（KingbaseES / PG 语法） |
| [db/kingbase/alert_config.sql](db/kingbase/alert_config.sql) | 配置表脚本（Kingbase，与 alert_info 同库） |
| [db/mysql/alert_info.sql](db/mysql/alert_info.sql) | 告警表脚本（MySQL 8.0 / 5.7） |
| [db/mysql/alert_config.sql](db/mysql/alert_config.sql) | 配置表脚本（MySQL，与 alert_info 同库） |
| [../alert-console-starter/README.md](../alert-console-starter/README.md) | 管理页面接入说明 |

## 本地端点

采集侧随应用启动提供两个端点（仅内网、无鉴权）：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/alert/refresh/local` | 重新加载 `alert_config`，返回版本与变更项 |
| `GET` | `/alert/effective` | 返回当前实例内存快照的生效值（仅阈值/开关，不含数据源密码） |

管理端（`alert-console-starter`）通过 `console.instances` 远程调用这两个端点，
因此业务服务**只需要引入 `alert-starter`**，不必引入 console。
业务应用的鉴权拦截器需放行 `/alert/**`。

## 模块结构

```
src/main/java/com/middleware/alert
├── AlertType / AlertLevel / AlertEvent / AlertReporter   对外模型与门面
├── exception/AlertableException                          业务可告警异常
├── config/                                               配置属性、运行时快照、解析、加载器
├── core/                                                 上下文、指纹、去重、限流、发布器、写入器
├── web/                                                  Controller 切面、异常分类、慢请求 filter
├── autoconfigure/                                        3 个自动配置类
└── util/JsonUtils                                        extra 字段极简序列化
```

## 验证

```bash
mvn test                              # 41 个单测 + 5 个端到端测试
mvn test -Dtest=AlertEndToEndTest     # 只跑端到端接入验证
cd ../alert-console-starter && mvn test   # 管理页面 10 个测试（配置/查询/大屏/刷新）
```

端到端测试用 H2 的 PostgreSQL / MySQL 兼容模式模拟实际接入方式（含吞异常的
`@ControllerAdvice`），覆盖自动装配、AOP、慢请求 Filter、批量落库，以及
`alert_config` 刷新后阈值动态生效。真实 Kingbase / MySQL、Apollo、Sleuth 需在内网按
[接入指南的验证清单](docs/INTEGRATION.md#82-内网真实环境自检清单) 验证。

## 构建

```bash
mvn test
mvn package
```

产物为普通 jar（不含 `BOOT-INF`），可直接被其他项目依赖。
