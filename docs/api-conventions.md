# 接口约定

## 1. 路径

- `/api/**`：管理端接口，只允许平台登录用户访问。
- `/open-api/**`：业务系统调用入口，使用 AppKey 和签名鉴权。
- `/actuator/**`：应用健康监控，只向运维网络开放。

## 2. 管理接口响应

```json
{
  "success": true,
  "data": {},
  "message": "success",
  "timestamp": "2026-08-10T03:00:00Z"
}
```

管理接口失败时使用明确 HTTP 状态码，同时返回可读错误信息。日志中记录详细异常，前端不展示堆栈。

## 3. 开放接口请求头

后续鉴权实现统一使用：

```text
X-App-Key: 调用方标识
X-Timestamp: Unix 毫秒时间戳
X-Nonce: 一次性随机字符串
X-Signature: 请求签名
X-Trace-Id: 可选；未传时由平台生成
```

平台响应始终返回 `X-Trace-Id`。签名串必须包含方法、路径、时间戳、Nonce 和请求体摘要，且校验时间窗口与 Nonce 防重放。

## 4. HTTP 转发语义

一期以契约一致的透明转发为主：保留目标接口约定的业务响应，不统一改造成管理接口的 `ApiResponse`。平台级错误使用固定错误码，例如：

- `IP-AUTH-001`：鉴权失败
- `IP-ROUTE-001`：接口未发布或已停用
- `IP-TARGET-001`：目标系统连接失败
- `IP-TIMEOUT-001`：目标接口调用超时
- `IP-SQL-001`：SQL 参数或只读校验失败

## 5. 日志与脱敏

每次调用至少记录 Trace ID、接口编码、调用方、目标系统、开始时间、耗时、HTTP 状态和平台结果。以下字段默认脱敏：`password`、`secret`、`token`、`authorization`、手机号、身份证号和银行卡号。

## 6. 已实现的管理接口

```text
GET/POST              /api/interfaces
GET/PUT/DELETE        /api/interfaces/{id}
PATCH                 /api/interfaces/{id}/enabled
GET                   /api/systems

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
```

## 7. SQL API 调用

管理员配置示例：

```sql
select system_code, system_name
from ip_system
where system_code = :code
```

调用方只提交参数：

```json
{
  "code": "WMS"
}
```

平台使用预编译参数执行，并返回 `traceId`、`rowCount`、`maxRows` 和 `rows`。调用方提交原始 SQL 会被拒绝。
