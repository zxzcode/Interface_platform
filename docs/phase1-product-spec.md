# 一期产品规格（验收基线）

## 1. 目标与范围

一期交付企业内部轻量接口平台，而不是通用 iPaaS：

1. 为 HTTP 接口提供统一的 `/open-api/**` 入口与透明转发，首批支持 WMS 调用既有 SAP HTTP 接口。
2. 管理多个只读数据源，并把管理员配置的单条只读 SQL 发布为参数化 API。
3. 提供可追溯的调用日志与 Trace ID。
4. 提供后台用户、调用方凭证与按 HTTP/SQL 资源编码的最小权限控制。

不在一期：可视化编排、复杂参数映射、消息总线、定时同步、自动重试、高可用集群、完整审计中心。

## 2. 已落地的后端能力

| 领域 | 当前代码能力 | 验收状态 |
|---|---|---|
| 管理身份 | JWT 登录、当前用户、改密、退出、用户 CRUD、ADMIN/OPERATOR/VIEWER | 待 Java 17 测试与角色联调 |
| 首位管理员 | 空用户表时从 `PLATFORM_ADMIN_*` 初始化 | 待部署验证 |
| 调用方 | AppKey、一次性 Secret、AES-GCM 密文、Secret 轮换 | 待前后端联调 |
| 外部认证 | HMAC-SHA256、5 分钟窗口、Nonce 唯一约束、逐资源授权 | 待真实接口联调 |
| HTTP | 动态路由、目标转发、超时、Trace ID、目标地址同源约束 | 待 WMS/SAP 联调 |
| SQL | 多数据源、只读连接池、命名参数、行数/超时限制、开放路由 | 待真实数据源联调 |
| 日志 | 调用日志列表、详情、请求/响应摘要脱敏 | 待异常场景验收 |

“待”不代表不可实现，而是当前没有完成构建、自动化测试或端到端证据。

## 3. 管理用户规则

- 首个 `ADMIN` 只在用户表为空且配置了 `PLATFORM_ADMIN_PASSWORD` 时创建；无硬编码默认密码。
- 密码使用 BCrypt；密码 8–72 位且不能为纯字母或纯数字。
- JWT 默认 120 分钟；用户停用、角色调整、改密、重置密码及退出后会使旧 Token 失效。
- `ADMIN` 管理用户与调用方；`OPERATOR` 管理接口、系统、数据源、SQL API；`VIEWER` 当前只具备管理端 GET 查看权限。
- 当前实现需修复/确认：VIEWER 的改密与退出请求被通用写权限规则拒绝。

## 4. 调用方交付与授权流程

1. ADMIN 创建调用方，提交唯一 `code`、名称、启停状态与完整的 HTTP/SQL 权限清单。
2. 平台生成 `AppKey` 和 `AppSecret`，仅在创建或轮换响应中返回一次 Secret；交付通过受控渠道完成。
3. 调用方将 Secret 保存到密钥服务或受保护的服务端配置；禁止放入浏览器、移动端、URL、日志和 Git。
4. 调用方按 [api-conventions.md](api-conventions.md) 的规范串对每次请求签名。
5. 平台校验身份、时间窗、Nonce、签名和资源权限，再执行 HTTP 或 SQL 路由。

当前后端将权限作为创建/更新调用方的请求字段，尚未发布前端正在调用的独立权限端点。该差异必须在一期验收前消除。

## 5. 鉴权逻辑

管理端使用 `Authorization: Bearer <JWT>`；外部调用使用以下四个必填头：

```text
X-App-Key
X-Timestamp
X-Nonce
X-Signature
```

规范串使用请求的**原始 Query**，不排序、不解码、不重编码：

```text
METHOD\nPATH\nRAW_QUERY\nTIMESTAMP\nNONCE\nSHA256_HEX(RAW_BODY_BYTES)
```

最终签名是 AppSecret 对该 UTF-8 规范串计算的 HMAC-SHA256 小写十六进制值。时间戳默认只允许相差 5 分钟；Nonce 在同一调用方范围内不可重复。精确命令见调用约定文档。

## 6. 一期发布门槛

只有以下各项均通过，才可把一期标为“完成”：

1. Java 17 后端测试、前端 TypeScript/生产构建、单 JAR 启动通过。
2. 登录、用户、系统、调用方页面与后端实际请求/响应契约完全一致。
3. 一个授权 HTTP 路由和一个授权 SQL 路由以真实 AppKey/Secret 成功调用。
4. 缺头、过期、错误签名、重复 Nonce、未授权资源均按预期拒绝并可用 Trace ID 查到日志。
5. WMS → 平台 → SAP 与至少一个业务只读数据源完成真实联调。
6. 日志中无明文密码、JWT、AppSecret、签名和未脱敏大正文。
7. 不修改既有 Flyway `V1`、`V2`；发布包不包含本地配置、构建产物、日志或密钥。

逐项执行记录见 [release-acceptance-checklist.md](release-acceptance-checklist.md)。
