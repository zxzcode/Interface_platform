# 总体架构

## 1. 架构结论

第一阶段采用“模块化单体 + 前后端合包”架构。一个 Spring Boot 进程同时提供管理 API、开放接口入口、HTTP 转发、只读 SQL 查询、调用日志和前端静态页面。

```text
业务系统 / 调用方
        │  AppKey + Signature + Trace ID
        ▼
  /open-api/** 统一入口
        │
        ├── 鉴权与接口权限
        ├── 请求校验与限流预留
        ├── HTTP 转发执行器 ──────► SAP / WMS / MES / OA / SRM
        ├── SQL 查询执行器 ───────► 多个只读业务数据源
        └── 调用日志与结果摘要

管理人员 ─► Vue 管理端 ─► /api/** ─► 配置与监控服务
```

不使用 Spring Cloud 的原因是：当前部署单元只有一个、第一阶段不要求独立扩缩容和高可用治理。先把模块边界、配置模型和安全规则做好，将来确有压力时再拆分网关、执行器和日志服务。

## 2. 模块划分

| 模块 | 责任 |
|---|---|
| interface-catalog | 接口定义、路径、方法、来源和目标系统 |
| http-forward | 构造目标请求、转发、超时控制和响应返回 |
| datasource | 数据源配置、凭证加密、连接池和连通性检测 |
| sql-query | SQL 模板、命名参数、只读校验、行数和超时限制 |
| invocation-log | Trace ID、请求摘要、响应摘要、耗时和错误信息 |
| access-control | 管理用户、AppKey、签名和接口授权 |
| dashboard | 汇总指标、趋势和系统健康状态 |

当前代码已建立 `common`、`config` 和 `platform` 基础包。后续实现业务写操作时，应按上述模块逐步拆包，不需要立即拆成多个 Maven 服务。

## 3. 请求链路

HTTP 转发链路：

1. 调用方访问 `/open-api/{interfacePath}`。
2. 平台根据 AppKey、时间戳、Nonce、签名校验调用身份。
3. 根据开放路径读取接口配置，并校验调用方权限。
4. 生成 Trace ID，记录入口时间和脱敏请求摘要。
5. 转发执行器调用目标系统，并执行连接/读取超时控制。
6. 记录目标状态码、响应摘要和耗时。
7. 在保持既定响应契约的前提下返回调用方。

SQL 查询链路：

1. 调用方访问已发布的 SQL API，而不是提交 SQL 文本。
2. 平台读取管理员配置的 SQL 模板和参数定义。
3. 验证只读语句、参数类型、最大行数和超时时间。
4. 选择对应数据源并使用预编译参数执行。
5. 对结果字段脱敏后返回并记录调用日志。

## 4. 部署形态

第一阶段推荐同一台服务器单独部署一个进程：

```text
interface-platform.jar
├── Spring Boot 后端
├── Vue dist 静态文件
└── Flyway 数据库迁移
```

它可以和现有 DataProcessor 位于同一台服务器，但使用独立端口、配置目录、日志目录、数据库账号和启动脚本。不要把接口平台直接合入正在生产运行的 DataProcessor 进程。

## 5. 后端代码分层

后端业务代码按职责统一归入三个同级目录：

```text
com.lzcer.interfaceplatform
├── controller   HTTP 接口入口
├── service      业务编排与事务
└── mapper       MyBatis 持久化接口
```

Mapper XML 统一放在 `server/src/main/resources/mapper/`。安全认证、动态数据源、只读 SQL 校验等基础设施仍保留独立包，避免与三层业务代码混放。
