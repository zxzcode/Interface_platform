# 开发指南

## 1. 环境要求

- JDK 17、Maven 3.9+
- MySQL 8
- 前端独立开发需要 Node.js 24.12+

完整构建会下载项目专用 Node.js，因此服务器只运行 JAR 时不需要安装 Node。

## 2. 代码模块

```text
server/src/main/java/com/lzcer/interfaceplatform/
├── common/             API 异常、凭证加密和日志脱敏
├── interfacecatalog/   HTTP 接口配置 CRUD 与路由解析
├── gateway/            开放入口、HTTP 转发和统一执行链路
├── invocationlog/      调用日志写入、列表和详情
├── datasource/         数据源 CRUD、凭证和动态连接池
├── sqlquery/           SQL API、只读校验和参数化执行
├── platform/           运行总览
└── config/             SPA 路由配置
```

## 3. 配置方式

默认激活 `local` 配置。开发机密码和加密密钥放在 Git 忽略的：

```text
config/application-local.yml
```

服务器推荐使用环境变量：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:PLATFORM_DB_URL = "jdbc:mysql://db-host:3306/interface_platform"
$env:PLATFORM_DB_USERNAME = "interface_platform"
$env:PLATFORM_DB_PASSWORD = "***"
$env:PLATFORM_ENCRYPTION_KEY = "Base64编码的32字节随机密钥"
```

`PLATFORM_ENCRYPTION_KEY` 上线后不得随意更换，否则已有数据源凭证无法解密。生产库建议给平台单独账号，不使用 root。

## 4. 本地开发

一键构建并启动：

```text
双击 start-interface-platform.bat
```

BAT 会先调用 `build.py`，成功后调用 `statr.py`/`start.py`；没有 Python 时自动回退到 PowerShell。若已有接口平台进程占用 JAR 或 8080 端口，需要先停止旧进程。

后端隔离测试使用 H2：

```powershell
mvn -pl server "-Dfrontend.skip=true" test
```

使用本地 MySQL 启动整包：

```powershell
.\scripts\build.ps1
.\scripts\start.ps1
```

前端热更新：

```powershell
cd frontend
npm install
npm run dev
```

Vite 会把 `/api`、`/open-api` 代理到 `http://localhost:8080`。

## 5. 关键安全边界

- HTTP 目标 URL 只能由管理员配置，必须是 HTTP/HTTPS，不能包含用户信息和片段。
- 转发不自动跟随重定向；过滤 Hop-by-Hop、Host、Content-Length 等请求头。
- 请求和响应最大 1 MiB；日志正文最大 16000 字符并自动脱敏。
- 数据源用户名和密码使用 AES-256-GCM 加密；运行池设置为只读，最大连接数 3。
- SQL API 只允许单条 `SELECT`，参数使用 `:name` 预编译绑定。
- SQL API 最大 5000 行、最大 60 秒，具体接口可配置更小限制。

## 6. 验证命令

```powershell
# 后端快速测试，不重复构建前端
mvn -pl server "-Dfrontend.skip=true" test

# 最终一体化构建
.\scripts\build.ps1
```

上线前还应验证：目标系统白名单策略、AppKey 签名鉴权、权限、限流和压力测试。
