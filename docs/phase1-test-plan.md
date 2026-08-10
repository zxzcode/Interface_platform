# 第一期测试计划

## 目标

验证接口平台一期的安全边界、开放调用约束和关键回归路径：后台 JWT 登录、用户 Token 失效、调用方 HMAC 鉴权与防重放、受控只读 SQL，以及调用日志脱敏。

## 自动化测试范围

| 场景 | 覆盖内容 | 自动化测试 |
|---|---|---|
| 后台登录 | JWT 签发、签名篡改、过期 Claims、Token Version 失效 | `JwtTokenServiceTests`、`InterfacePlatformApplicationTests` |
| 开放调用 | 规范串、错误签名、5 分钟时间窗、Nonce 唯一约束 | `ExternalApiAuthServiceTests` |
| SQL API | 仅单条 SELECT、命名参数、注释/多语句/写操作/锁定与导出拦截 | `ReadOnlySqlValidatorTests` |
| 调用日志 | 请求头、JSON/Form 敏感字段、二进制内容、过长文本脱敏与截断 | `PayloadSanitizerTests` |
| 加密存储 | 同文不同密文、AES-GCM 解密回读 | `CredentialCipherTests` |

测试使用内存 H2 数据库和仅用于测试的固定密钥；不读取或写入本机 `config/application-local.yml`，也不会接触真实 AppSecret、账号或业务数据。

## 执行方式

在仓库根目录执行。脚本会自动定位 Java 17 及以上版本；本机默认 Java 8 不能用于本项目。

```powershell
$env:JAVA_HOME = "C:\\Users\\haojiang\\.jdks\\ms-17.0.15"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
mvn -pl server -am '-Dfrontend.skip=true' test
```

完整交付构建仍由主线程执行：

```powershell
.\\scripts\\build.ps1
```

## 人工验收清单

1. 管理员首次由环境变量初始化，登录后能创建、禁用、改密和删除用户；最后一个启用管理员不能被移除。
2. 管理端创建调用方时仅展示一次 AppSecret；后续列表和日志均不应出现明文 Secret。
3. 调用方必须携带 `X-App-Key`、`X-Timestamp`、`X-Nonce`、`X-Signature`；重放相同 Nonce 返回 `IP-SIGN-007`。
4. 未获 HTTP/SQL 接口授权的调用方返回 `IP-SIGN-008`，且调用日志保留 Trace ID 与失败原因，不含敏感值。
5. SQL API 只能调用已发布模板，验证数据源为只读账号、行数和超时限制有效。
6. 接口目标地址必须与已配置的目标系统 Base URL 同源，不能通过配置访问任意地址。

## 本次执行结果

2026-08-10 使用 Microsoft OpenJDK 17.0.15 执行：

```text
mvn -pl server -am '-Dfrontend.skip=true' test
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
```

测试运行期间通过 H2 内存数据库执行了全部 Flyway V1、V2、V3 迁移；未修改已发布迁移，也未访问本机 MySQL、真实密钥或业务数据。完整前后端打包和部署验收由主线程继续执行。
