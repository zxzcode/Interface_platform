import axios, { type AxiosRequestConfig } from 'axios'
import { clearAccessToken, getAccessToken } from '@/auth/session'

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
export interface LoginResult { token: string; user: UserAccount }
export interface UserCommand {
  username: string; displayName: string; role: PlatformRole; enabled: boolean; password?: string
}

export interface ApiClientSummary {
  id: number; name: string; description?: string; appKey: string; enabled: boolean
  createdAt?: string; updatedAt?: string
}
export interface ApiClientCommand { name: string; description?: string; enabled: boolean }
export interface ClientSecretResult { id?: number; appKey: string; appSecret: string }
export type PermissionResourceType = 'HTTP' | 'SQL'
export interface ClientPermission { resourceType: PermissionResourceType; resourceCode: string }

export interface SystemCommand { code: string; name: string; baseUrl?: string; status: string; description?: string }

export interface TrendPoint { time: string; total: number; success: number }
export interface SystemStatus { code: string; name: string; status: 'ONLINE' | 'DEGRADED' | 'OFFLINE' | 'UNKNOWN' }
export interface DashboardSummary {
  todayCalls: number; successRate: number; failedCalls: number; averageDurationMs: number
  activeInterfaces: number; activeDatasources: number; trend: TrendPoint[]; systems: SystemStatus[]
}

export interface SystemOption { id: number; code: string; name: string; baseUrl?: string; status: string; description?: string; updatedAt?: string }
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
  logout: () => requestData<void>({ url: '/auth/logout', method: 'POST' }),

  dashboard: () => requestData<DashboardSummary>({ url: '/dashboard' }),
  systems: () => requestData<SystemOption[]>({ url: '/systems' }),
  createSystem: (data: SystemCommand) => requestData<SystemOption>({ url: '/systems', method: 'POST', data }),
  updateSystem: (id: number, data: SystemCommand) => requestData<SystemOption>({ url: `/systems/${id}`, method: 'PUT', data }),
  deleteSystem: (id: number) => requestData<void>({ url: `/systems/${id}`, method: 'DELETE' }),

  users: () => requestData<UserAccount[]>({ url: '/users' }),
  createUser: (data: UserCommand) => requestData<UserAccount>({ url: '/users', method: 'POST', data }),
  updateUser: (id: number, data: UserCommand) => requestData<UserAccount>({ url: `/users/${id}`, method: 'PUT', data }),
  deleteUser: (id: number) => requestData<void>({ url: `/users/${id}`, method: 'DELETE' }),

  clients: () => requestData<ApiClientSummary[]>({ url: '/clients' }),
  createClient: (data: ApiClientCommand) => requestData<ClientSecretResult>({ url: '/clients', method: 'POST', data }),
  updateClient: (id: number, data: ApiClientCommand) => requestData<ApiClientSummary>({ url: `/clients/${id}`, method: 'PUT', data }),
  deleteClient: (id: number) => requestData<void>({ url: `/clients/${id}`, method: 'DELETE' }),
  rotateClientSecret: (id: number) => requestData<ClientSecretResult>({ url: `/clients/${id}/rotate-secret`, method: 'POST' }),
  clientPermissions: (id: number) => requestData<ClientPermission[]>({ url: `/clients/${id}/permissions` }),
  updateClientPermissions: (id: number, permissions: ClientPermission[]) => requestData<ClientPermission[]>({
    url: `/clients/${id}/permissions`, method: 'PUT', data: { permissions },
  }),

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
