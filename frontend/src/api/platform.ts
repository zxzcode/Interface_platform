import axios, { type AxiosRequestConfig } from 'axios'
import { clearAccessToken, getAccessToken } from '@/auth/session'

// 此客户端只服务管理端 /api；开放接口使用 HMAC 协议，不能复用浏览器 JWT。
const http = axios.create({ baseURL: '/api', timeout: 30_000 })

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  response => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && error.config?.url !== '/auth/login') {
      // 令牌过期或被撤销时，通知应用统一清理状态并跳回登录页。
      clearAccessToken()
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    return Promise.reject(error)
  },
)

interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
  timestamp: string
}

export type PlatformRole = 'ADMIN' | 'OPERATOR' | 'VIEWER'
export interface UserAccount {
  id: number; username: string; displayName: string; role: PlatformRole; enabled: boolean
  lastLoginAt?: string; createdAt?: string; updatedAt?: string
}
export interface LoginCommand { username: string; password: string }
export interface LoginResult { accessToken: string; tokenType: 'Bearer'; expiresAt: string; user: UserAccount }
export interface CreateUserCommand {
  username: string; displayName: string; role: PlatformRole; enabled: boolean; password: string
}
export interface UpdateUserCommand { displayName: string; role: PlatformRole; enabled: boolean }
export interface PasswordCommand { password: string }
export interface ChangePasswordCommand { currentPassword: string; newPassword: string }

export interface ApiClientSummary {
  id: number; code: string; name: string; appKey: string; enabled: boolean; permissions: ClientPermission[]
  createdAt?: string; updatedAt?: string
}
export interface CreateApiClientCommand { code: string; name: string; enabled: boolean; permissions: ClientPermission[] }
export interface UpdateApiClientCommand { name: string; enabled: boolean; permissions: ClientPermission[] }
export interface ClientSecretResult { client: ApiClientSummary; appSecret: string }
export type PermissionResourceType = 'HTTP' | 'SQL'
export interface ClientPermission { routeType: PermissionResourceType; resourceCode: string }

export interface SystemCommand { code: string; name: string; baseUrl: string; status: string }

export interface TrendPoint { time: string; total: number; success: number }
export interface SystemStatus { code: string; name: string; status: 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN' }
export interface DashboardSummary {
  todayCalls: number; successRate: number; failedCalls: number; averageDurationMs: number
  activeInterfaces: number; activeDatasources: number; trend: TrendPoint[]; systems: SystemStatus[]
}

export interface SystemOption { id: number; code: string; name: string; baseUrl: string; status: string; createdAt?: string; updatedAt?: string }
export interface InterfaceSummary {
  id: number; code: string; name: string; description?: string
  sourceSystemId: number; sourceSystem: string; targetSystemId: number; targetSystem: string
  method: string; path: string; targetUrl: string; connectTimeoutMs: number; readTimeoutMs: number
  enabled: boolean; todayCalls: number; successRate: number; averageDurationMs: number; updatedAt: string
}
export interface InterfaceCommand {
  code: string; name: string; description?: string; sourceSystemId: number | null; targetSystemId: number | null
  method: string; path: string; targetUrl: string; connectTimeoutMs: number; readTimeoutMs: number; enabled: boolean
}

export interface DataSourceSummary {
  id: number; code: string; name: string; dbType: string; jdbcUrl: string; driverClassName: string
  status: 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN'; enabled: boolean
  credentialConfigured: boolean; poolUsage: number; lastCheckedAt?: string; updatedAt: string
}
export interface DataSourceCommand {
  code: string; name: string; dbType: string; jdbcUrl: string; driverClassName?: string
  username?: string; password?: string; enabled: boolean
}
export interface ConnectionTestResult { success: boolean; durationMs: number; message: string }

export interface SqlApiSummary {
  id: number; code: string; name: string; description?: string; path: string; method: string
  datasourceId: number; datasourceName: string; sql: string; timeoutSeconds: number
  maxRows: number; enabled: boolean; updatedAt: string
}
export interface SqlApiCommand {
  code: string; name: string; description?: string; path: string; method: string; datasourceId: number | null
  sql: string; timeoutSeconds: number; maxRows: number; enabled: boolean
}
export interface SqlQueryResult { rowCount: number; maxRows: number; rows: Record<string, unknown>[] }

export interface InvocationLog {
  traceId: string; routeType: 'HTTP' | 'SQL' | 'ROUTE'; interfaceCode: string; interfaceName: string
  caller: string; targetSystem: string; requestMethod: string; requestPath: string
  status: 'SUCCESS' | 'FAILED' | 'RUNNING'; platformCode?: string; httpStatus: number
  durationMs: number; requestTime: string
}
export interface InvocationLogDetail extends InvocationLog {
  targetAddress?: string; requestHeaders?: string; requestSummary?: string
  responseHeaders?: string; responseSummary?: string; errorMessage?: string; completedAt?: string
}

async function requestData<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>(config)
    if (response.status === 204) return undefined as T
    // 后端业务异常也使用统一外层结构，HTTP 200 不代表本次管理操作成功。
    if (!response.data?.success) throw new Error(response.data?.message || '请求失败')
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      throw new Error(error.response?.data?.message || error.message || '网络请求失败')
    }
    throw error
  }
}

export const platformApi = {
  login: (data: LoginCommand) => requestData<LoginResult>({ url: '/auth/login', method: 'POST', data }),
  currentUser: () => requestData<UserAccount>({ url: '/auth/me' }),
  changePassword: (data: ChangePasswordCommand) => requestData<void>({ url: '/auth/password', method: 'POST', data }),
  logout: () => requestData<void>({ url: '/auth/logout', method: 'POST' }),

  dashboard: () => requestData<DashboardSummary>({ url: '/dashboard' }),
  systems: () => requestData<SystemOption[]>({ url: '/systems' }),
  createSystem: (data: SystemCommand) => requestData<SystemOption>({ url: '/systems', method: 'POST', data }),
  updateSystem: (id: number, data: SystemCommand) => requestData<SystemOption>({ url: `/systems/${id}`, method: 'PUT', data }),
  deleteSystem: (id: number) => requestData<void>({ url: `/systems/${id}`, method: 'DELETE' }),

  users: () => requestData<UserAccount[]>({ url: '/users' }),
  createUser: (data: CreateUserCommand) => requestData<UserAccount>({ url: '/users', method: 'POST', data }),
  updateUser: (id: number, data: UpdateUserCommand) => requestData<UserAccount>({ url: `/users/${id}`, method: 'PUT', data }),
  resetUserPassword: (id: number, data: PasswordCommand) => requestData<void>({ url: `/users/${id}/password`, method: 'PATCH', data }),
  deleteUser: (id: number) => requestData<void>({ url: `/users/${id}`, method: 'DELETE' }),

  clients: () => requestData<ApiClientSummary[]>({ url: '/clients' }),
  createClient: (data: CreateApiClientCommand) => requestData<ClientSecretResult>({ url: '/clients', method: 'POST', data }),
  updateClient: (id: number, data: UpdateApiClientCommand) => requestData<ApiClientSummary>({ url: `/clients/${id}`, method: 'PUT', data }),
  deleteClient: (id: number) => requestData<void>({ url: `/clients/${id}`, method: 'DELETE' }),
  rotateClientSecret: (id: number) => requestData<ClientSecretResult>({ url: `/clients/${id}/rotate-secret`, method: 'POST' }),

  interfaces: () => requestData<InterfaceSummary[]>({ url: '/interfaces' }),
  createInterface: (data: InterfaceCommand) => requestData<InterfaceSummary>({ url: '/interfaces', method: 'POST', data }),
  updateInterface: (id: number, data: InterfaceCommand) => requestData<InterfaceSummary>({ url: `/interfaces/${id}`, method: 'PUT', data }),
  setInterfaceEnabled: (id: number, enabled: boolean) => requestData<InterfaceSummary>({ url: `/interfaces/${id}/enabled`, method: 'PATCH', data: { enabled } }),
  deleteInterface: (id: number) => requestData<void>({ url: `/interfaces/${id}`, method: 'DELETE' }),

  datasources: () => requestData<DataSourceSummary[]>({ url: '/datasources' }),
  createDatasource: (data: DataSourceCommand) => requestData<DataSourceSummary>({ url: '/datasources', method: 'POST', data }),
  updateDatasource: (id: number, data: DataSourceCommand) => requestData<DataSourceSummary>({ url: `/datasources/${id}`, method: 'PUT', data }),
  testDatasource: (id: number) => requestData<ConnectionTestResult>({ url: `/datasources/${id}/test`, method: 'POST' }),
  deleteDatasource: (id: number) => requestData<void>({ url: `/datasources/${id}`, method: 'DELETE' }),

  sqlApis: () => requestData<SqlApiSummary[]>({ url: '/sql-apis' }),
  createSqlApi: (data: SqlApiCommand) => requestData<SqlApiSummary>({ url: '/sql-apis', method: 'POST', data }),
  updateSqlApi: (id: number, data: SqlApiCommand) => requestData<SqlApiSummary>({ url: `/sql-apis/${id}`, method: 'PUT', data }),
  setSqlApiEnabled: (id: number, enabled: boolean) => requestData<SqlApiSummary>({ url: `/sql-apis/${id}/enabled`, method: 'PATCH', data: { enabled } }),
  testSqlApi: (id: number, parameters: Record<string, unknown>) => requestData<SqlQueryResult>({ url: `/sql-apis/${id}/test`, method: 'POST', data: parameters }),
  deleteSqlApi: (id: number) => requestData<void>({ url: `/sql-apis/${id}`, method: 'DELETE' }),

  logs: (limit = 100) => requestData<InvocationLog[]>({ url: `/logs?limit=${limit}` }),
  logDetail: (traceId: string) => requestData<InvocationLogDetail>({ url: `/logs/${encodeURIComponent(traceId)}` }),
}
