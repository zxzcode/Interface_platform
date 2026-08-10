# 数据库设计

平台基础库当前使用 MySQL 8，数据库名为 `interface_platform`。Flyway 脚本同时兼容测试环境 H2。

| 表 | 用途 |
|---|---|
| `ip_system` | WMS、SAP、MES 等系统档案和健康状态 |
| `ip_interface` | HTTP 接口定义和运行指标 |
| `ip_datasource` | 多数据源连接配置 |
| `ip_sql_api` | 管理员配置的只读 SQL API |
| `ip_api_client` | 外部调用方 AppKey 和加密 Secret |
| `ip_invocation_log` | 调用链路、状态、耗时和脱敏摘要 |

`ip_interface` 保存开放路径、目标完整 URL、HTTP 方法及连接/响应超时；`ip_datasource` 保存 JDBC 地址和 AES-GCM 密文；`ip_sql_api` 保存管理员审核后的 SELECT 模板、开放路径、最大行数和执行超时。

## 设计要求

- 表主键使用 `bigint`，业务标识使用唯一编码。
- 密钥字段只能保存密文，字段名使用 `encrypted_` 前缀提醒开发者。
- 请求、响应正文第一阶段只保留脱敏摘要；大正文不直接写数据库。
- 调用日志按时间、接口编码、执行状态建立索引。
- 生产环境日志增长后按月份分区或归档，第一阶段先保留 90 天。
- 每次结构调整新增 `V{n}__description.sql`，禁止修改已经部署过的版本。
- 本地和生产平台基础库使用 MySQL；自动化测试使用 H2。
- 业务数据源与平台配置库不是同一个概念，业务数据源必须使用只读账户。
- `config/application-local.yml` 仅用于本机且已被 Git 忽略；生产密码通过环境变量或密钥服务注入。
