# Interface Platform

面向 WMS、SAP、MES、OA、SRM 等企业系统的轻量接口平台。当前版本已形成 HTTP 转发、多数据源只读 SQL 查询、后台用户管理、调用方鉴权和完整调用日志的可运行闭环。

## 已实现能力

- HTTP 接口配置 CRUD、启停、连接/响应超时和在线测试
- `/open-api/**` 动态路由，透明转发 Method、Query、Header、Body 和目标响应
- 请求体与响应体大小限制、禁用自动重定向、Trace ID 响应头
- 请求头、响应头、请求/响应摘要、异常、状态码和耗时完整日志
- 数据源 CRUD、AES-256-GCM 凭证加密、只读小型连接池和连接测试
- 参数化 SQL API CRUD、发布、在线测试、最大行数和执行超时
- SQL 安全校验：只允许单条 `SELECT`，拒绝注释、多语句和写操作关键字
- 管理端 JWT 登录、ADMIN/OPERATOR/VIEWER 角色权限和用户管理
- 调用方 AppKey/AppSecret 管理、HTTP/SQL 接口逐项授权
- 外部调用 HMAC-SHA256 签名、时间窗校验和 Nonce 防重放
- 系统档案 CRUD，HTTP 目标地址与目标系统基础地址同源校验
- Vue 3 管理端与 Spring Boot 后端合并为一个可执行 JAR

一期暂不包含：限流、高可用、复杂参数映射、可视化编排、MQ、定时同步和 Spring Cloud 拆分。

## 技术栈

- Java 17、Spring Boot 4.1、Spring MVC、JdbcClient、Flyway
- MySQL 8（平台基础库）
- MySQL、SQL Server、PostgreSQL 动态业务数据源
- Vue 3、TypeScript、Vite、Element Plus

## 本机数据库

本地平台基础库为 `interface_platform`。账号密码保存在 Git 忽略的 `config/application-local.yml`，不会提交到仓库。

当前还创建了一个只有 `SELECT` 权限的 `interface_query@127.0.0.1` 示例账号，管理端中已配置“本地平台只读库”和 `/open-api/sql/system-query` 参数化查询示例。

## 构建与启动

Windows 可直接双击根目录的 `start-interface-platform.bat`。它会依次执行 `build.py` 和 `statr.py`（兼容拼写，实际入口为 `start.py`），构建成功后启动项目；没有安装 Python 时自动回退到 PowerShell 启动脚本。

也可以在 PowerShell 中执行：

```powershell
cd E:\Code\interface_platform
.\scripts\build.ps1
.\scripts\start.ps1
```

浏览器访问：<http://localhost:8080>

部署只需要复制 `server/target/interface-platform.jar`、准备外部配置或环境变量，并使用 Java 17 启动。

## 示例调用

```powershell
$env:INTERFACE_APP_KEY = "创建调用方时取得的 AppKey"
$env:INTERFACE_APP_SECRET = "创建调用方时取得的 AppSecret"
.\scripts\call-open-api.ps1 -Path "/open-api/sql/system-query" -Body '{"code":"WMS"}'
```

响应头包含 `X-Trace-Id`，可在“调用日志”页面查看脱敏后的完整链路信息。

## 文档

- [总体架构](docs/architecture.md)
- [开发指南](docs/development.md)
- [前端设计规范](docs/frontend-design.md)
- [数据库设计](docs/database.md)
- [接口约定](docs/api-conventions.md)
- [一期开发路线](docs/roadmap.md)
- [一期产品规格](docs/phase1-product-spec.md)
