# 接口约定与联调状态

> 本文以当前控制器、服务实现和前端请求代码为依据（2026-08-10）。其中“待联调/待开发”不是已通过的功能，不能据此安排生产接入。

## 1. 入口与通用响应

- `/api/**`：平台管理 API，使用 JWT Bearer Token。
- `/open-api/**`：外部业务系统调用入口，使用 `AppKey + HMAC-SHA256`；不接受 JWT 代替签名。
- `/actuator/health`：当前匿名可访问的存活检查；其余 Actuator 端点不应暴露到公网。

管理 API 的成功响应使用 `ApiResponse<T>`：

```json
{"success":true,"data":{},"message":"success","timestamp":"2026-08-10T03:00:00Z"}
```

开放调用成功时，HTTP 转发保留目标系统的状态码、响应头和响应体；SQL API 返回 JSON。平台自身拒绝时返回：

```json
{"success":false,"code":"IP-SIGN-006","message":"请求签名无效","traceId":"T..."}
```

所有经网关处理的响应均带 `X-Trace-Id`。

## 2. 当前后台认证与角色

### JWT

`POST /api/auth/login` 不需要 Token，请求体：

```json
{"username":"operator","password":"由管理员分配的密码"}
```

当前后端响应字段为 `accessToken`、`tokenType`（`Bearer`）、`expiresAt` 和 `user`。后续管理请求使用：

```text
Authorization: Bearer <accessToken>
```

令牌默认 120 分钟有效。用户被停用、角色变更、改密、管理员重置密码或退出登录后，服务端递增 `token_version`，旧令牌会失效。

已发布的认证端点：

```text
POST  /api/auth/login
GET   /api/auth/me
POST  /api/auth/password       # 当前用户修改密码
POST  /api/auth/logout         # 使当前账号现有 JWT 失效
```

### 实际后端授权矩阵

| 操作 | ADMIN | OPERATOR | VIEWER |
|---|---:|---:|---:|
| 查看 `/api/**` 资源 | 是 | 是 | 是 |
| 修改接口、系统、数据源、SQL API | 是 | 是 | 否 |
| 管理用户、调用方 | 是 | 否 | 否 |
| `POST /api/auth/password`、`/logout` | 是 | 是 | **当前否** |

最后一项来自当前 Spring Security 的通配规则：VIEWER 只能访问 GET `/api/**`。产品预期是所有已登录用户可改自己的密码并退出，需在后续代码修复后再验收。

## 3. 初始管理员与安全配置

当 `ip_user` 没有任何用户时，应用启动会读取下列环境变量创建首位 `ADMIN`；若没有设置 `PLATFORM_ADMIN_PASSWORD`，应用只记录警告，不创建默认账号。

```powershell
$env:PLATFORM_ADMIN_USERNAME = 'admin'             # 可省略，默认 admin
$env:PLATFORM_ADMIN_NAME = '系统管理员'              # 可省略
$env:PLATFORM_ADMIN_PASSWORD = '<仅在安全终端设置>'
$env:PLATFORM_JWT_KEY = '<Base64 编码且至少 32 字节的随机密钥>'
$env:PLATFORM_ENCRYPTION_KEY = '<与 JWT 密钥不同的 Base64 随机密钥>'
```

密码要求为 8–72 位，且不能全是字母或全是数字。生产环境不得把上述值写入 Git、前端配置或日志；`PLATFORM_JWT_KEY` 不应依赖加密密钥的回退逻辑，应显式单独设置。

## 4. 用户、系统与调用方管理 API

以下为已在后端控制器发布的端点，均返回管理 API 通用响应（删除成功返回 HTTP 204）：

```text
GET/POST          /api/users                         # 仅 ADMIN
PUT/DELETE        /api/users/{id}                    # 仅 ADMIN
PATCH             /api/users/{id}/password           # 仅 ADMIN

GET/POST          /api/systems
PUT/DELETE        /api/systems/{id}

GET/POST          /api/clients                       # 仅 ADMIN
PUT/DELETE        /api/clients/{id}                  # 仅 ADMIN
POST              /api/clients/{id}/rotate-secret    # 仅 ADMIN
```

系统写入参数为 `code`、`name`、`baseUrl`、`status`；`baseUrl` 必填，只能是没有账号信息、查询串和片段的 HTTP/HTTPS 地址。

调用方创建请求的实际后端契约为：

```json
{
  "code":"WMS_PROD",
  "name":"WMS 生产系统",
  "enabled":true,
  "permissions":[
    {"routeType":"HTTP","resourceCode":"SAP_ORDER_CREATE"},
    {"routeType":"SQL","resourceCode":"STOCK_QUERY"}
  ]
}
```

创建和轮换的响应结构为 `data.client` 与 `data.appSecret`；`appSecret` 只在该次响应中返回，服务端仅保存 AES-GCM 密文。更新调用方也必须提交完整的 `permissions` 列表，空数组表示收回全部权限。

**待联调：** 当前前端使用了 `description` 字段、扁平的 `appKey/appSecret` 响应，并调用了尚未发布的 `GET/PUT /api/clients/{id}/permissions`；它与上述后端契约不一致。调用方页面、创建、权限编辑和密钥展示均不能标记为可用，须先统一前后端契约。

## 5. HTTP、数据源和 SQL API 管理端点

```text
GET/POST              /api/interfaces
GET/PUT/DELETE        /api/interfaces/{id}
PATCH                 /api/interfaces/{id}/enabled
POST                  /api/interfaces/{id}/test

GET/POST              /api/datasources
GET/PUT/DELETE        /api/datasources/{id}
PATCH                 /api/datasources/{id}/enabled
POST                  /api/datasources/{id}/test

GET/POST              /api/sql-apis
GET/PUT/DELETE        /api/sql-apis/{id}
PATCH                 /api/sql-apis/{id}/enabled
POST                  /api/sql-apis/{id}/test

GET                   /api/logs?limit=100
GET                   /api/logs/{traceId}
GET                   /api/dashboard
```

接口配置的 `targetUrl` 必须与目标系统 `baseUrl` 使用相同协议、主机和有效端口；这是当前已实现的目标地址约束。它不是基于 DNS/IP 网段的生产级白名单，后者仍待设计和验证。

SQL 模板由管理端配置，网关只接收参数：GET 从查询串读取，存在请求体时必须是 JSON 对象；请求体参数会覆盖同名 Query 参数。SQL 的读取语义、驱动差异和真实数据源权限仍需结合目标库联测。

## 6. 开放调用鉴权规范

每个 `/open-api/**` 请求都必须带：

```text
X-App-Key: <AppKey>
X-Timestamp: <Unix 毫秒时间戳>
X-Nonce: <8-100 位字母、数字、点、下划线或短横线>
X-Signature: <小写十六进制 HMAC-SHA256>
X-Trace-Id: <可选，8-64 位合法字符>
```

规范串严格为六行，最后一行后**不追加换行**：

```text
HTTP_METHOD（大写）
OPEN_API_PATH
RAW_QUERY
TIMESTAMP
NONCE
SHA256_HEX(RAW_BODY_BYTES)
```

- `OPEN_API_PATH` 为实际请求的路径，例如 `/open-api/stock/query`。
- `RAW_QUERY` 是 URL 中 `?` 后的原始字符序列；没有查询参数则为空行。
- 不得对 Query 做排序、解码、重新编码、增删参数或改变参数顺序；签名计算和实际请求必须使用完全相同的原始 Query。
- 空请求体的摘要是空字节数组的 SHA-256 值。
- 最终签名：`HMAC_SHA256_HEX(UTF8(AppSecret), UTF8(canonical))`，输出小写十六进制。

服务器允许时间偏差默认前后 300 秒。签名验证通过后会写入 Nonce；同一调用方重复 Nonce 会被拒绝。HTTP 与 SQL 路由解析后都根据 `routeType + resourceCode` 校验调用方显式权限。

当前实现使用的主要签名错误码为：`IP-SIGN-001`（缺少头）、`002`（Nonce 格式）、`003`（时间戳格式）、`004`（过期）、`005`（无效/停用 AppKey）、`006`（签名错误）、`007`（重放）、`008`（无资源权限）。

## 7. PowerShell 真实调用步骤

前提：管理员已创建调用方、将 HTTP/SQL 资源授权给该调用方，并且接口已启用。以下示例只使用环境变量占位，不要把 Secret 保存进脚本或命令历史。

```powershell
# 在当前 PowerShell 会话临时设置；值由调用方创建/轮换时一次性取得
$env:INTERFACE_APP_KEY = '<你的 AppKey>'
$env:INTERFACE_APP_SECRET = '<你的 AppSecret>'

# POST；脚本按当前请求的原始 Body、Path、Query 自动生成签名
.\scripts\call-open-api.ps1 `
  -BaseUrl 'http://localhost:8080' `
  -Path '/open-api/stock/query' `
  -Method 'POST' `
  -Query 'warehouseId=WH01&sku=A001' `
  -Body '{"page":1,"size":20}'
```

脚本计算的 `Query` 就是规范串第三行，并以同一字符序列附加到 URL。调用结果会打印 HTTP 状态和 `X-Trace-Id`；用该 Trace ID 到日志页查询。不要在 `-Query` 中先排序或 URL 编解码后又用另一种形式发起请求。

## 8. 本轮必须完成的联调项

1. 将登录页由 `token` 改为读取后端的 `accessToken`，并验证登录、刷新、退出和 401 处理。
2. 对齐用户、系统、调用方页面的请求/响应字段；尤其是调用方的 `code`、`permissions` 和嵌套返回值。
3. 选择一种权限编辑契约：在前端随调用方 POST/PUT 提交完整权限，或新增并测试独立权限 API；二选一后删除另一套假设。
4. 使用 PowerShell 脚本对一个真实 HTTP 路由和一个 SQL 路由验证：正确签名、过期时间戳、重复 Nonce、错误签名、未授权资源。
5. 以 Java 17 完成后端测试、前端生产构建和单 JAR 启动验证后，才更新验收结论。
