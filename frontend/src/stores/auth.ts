import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { platformApi, type LoginCommand, type PlatformRole, type UserAccount } from '@/api/platform'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/auth/session'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getAccessToken())
  const user = ref<UserAccount | null>(null)
  const initializing = ref(false)
  let initialized = false

  const authenticated = computed(() => Boolean(token.value && user.value))
  const role = computed<PlatformRole | null>(() => user.value?.role ?? null)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const canOperate = computed(() => role.value === 'ADMIN' || role.value === 'OPERATOR')

  function acceptToken(value: string): void {
    token.value = value
    setAccessToken(value)
  }

  function clearSession(): void {
    token.value = null
    user.value = null
    clearAccessToken()
  }

  async function login(command: LoginCommand): Promise<UserAccount> {
    const result = await platformApi.login(command)
    acceptToken(result.token)
    user.value = result.user
    initialized = true
    return result.user
  }

  async function initialize(): Promise<void> {
    if (initialized || initializing.value) return
    initialized = true
    if (!token.value) return
    initializing.value = true
    try {
      user.value = await platformApi.currentUser()
    } catch {
      clearSession()
    } finally {
      initializing.value = false
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) await platformApi.logout()
    } finally {
      clearSession()
    }
  }

  return { token, user, authenticated, role, isAdmin, canOperate, initializing, login, initialize, logout, clearSession }
})

