// 使用 sessionStorage：关闭浏览器标签后令牌自动失效，避免在共享电脑长期保留管理端登录态。
const TOKEN_KEY = 'interface-platform.access-token'

export function getAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}
