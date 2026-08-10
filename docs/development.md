# 开发与验证指南

## 1. 环境基线

- 后端：Java 17、Maven 3.9+、MySQL 8。
- 前端独立开发：Node.js 24.12+。
- 当前终端默认的 JDK 8 不能用于该项目；不要直接使用默认 `mvn` 作为验收依据。

项目统一构建入口会选择 Java 17：

```powershell
Set-Location E:\Code\interface_platform
.\scripts\build.ps1
```

如需单独执行 Maven，先显式将 `JAVA_HOME` 与 `PATH` 指向 Java 17，再执行相应命令。构建输出、日志、本地配置、密钥和数据均不得提交 Git。

## 2. 本地安全配置

`config/application-local.yml` 已被忽略，适合放本机 MySQL 地址与开发密钥。部署环境优先使用环境变量：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:PLATFORM_DB_URL = 'jdbc:mysql://db-host:3306/interface_platform'
$env:PLATFORM_DB_USERNAME = 'interface_platform'
$env:PLATFORM_DB_PASSWORD = '<数据库密码>'
$env:PLATFORM_ENCRYPTION_KEY = '<Base64 随机密钥>'
$env:PLATFORM_JWT_KEY = '<独立的 Base64 随机密钥>'
$env:PLATFORM_ADMIN_PASSWORD = '<首次启动时创建管理员使用>'
```

`PLATFORM_ADMIN_PASSWORD` 只在用户表为空时被使用；创建首位管理员后应从部署环境的常驻配置中移除或轮换。加密密钥变更会导致已存储的数据源凭证和 AppSecret 密文无法解密，变更前必须有密钥迁移方案。

## 3. 开发运行

```powershell
# 全量构建（推荐，采用 Java 17）
.\scripts\build.ps1

# 启动已构建的单 JAR
.\scripts\start.ps1
```

桌面环境可使用根目录 `start-interface-platform.bat`。前端热更新需要在 `frontend` 目录安装依赖后执行 `npm run dev`，开发代理将 `/api` 与 `/open-api` 转发到 `http://localhost:8080`。

## 4. 数据库迁移约束

- 仅新增 Flyway 迁移文件。
- 已发布的 `V1`、`V2` 以及已被环境执行的 `V3` 不得修改。
- 当前 `V3__access_control.sql` 创建用户、调用方权限与 Nonce 防重放相关表；在新环境需确认迁移按版本顺序成功执行。
- 真实业务数据源使用单独的只读账号；平台库账号与业务库账号不得混用。

## 5. 最小验证顺序

1. 使用 Java 17 运行后端测试。
2. 运行前端 TypeScript 检查和生产构建；Node 版本不足时先升级，不把“未运行”标为通过。
3. 启动单 JAR，设置首位管理员环境变量，完成登录和 `/api/auth/me`。
4. 创建目标系统、HTTP 接口、只读数据源和 SQL API，并分别执行管理端测试。
5. 创建调用方并完成权限配置（当前该页与后端契约待对齐）。
6. 用 `scripts/call-open-api.ps1` 成功调用一次 HTTP 和一次 SQL；再验证过期时间戳、重复 Nonce、错误签名和未授权资源。
7. 以 `X-Trace-Id` 查询日志，确认请求/响应摘要已脱敏且不含密码、JWT 或 AppSecret。

详细接口和 PowerShell 签名步骤见 [api-conventions.md](api-conventions.md)。完整发布核对项见 [release-acceptance-checklist.md](release-acceptance-checklist.md)。
