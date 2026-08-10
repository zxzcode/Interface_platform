import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import type { PlatformRole } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import { pinia } from '@/stores/pinia'

function allowedRoles(route: RouteLocationNormalized): PlatformRole[] | undefined {
  return route.meta.roles as PlatformRole[] | undefined
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录', public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '运行总览' } },
    { path: '/interfaces', name: 'interfaces', component: () => import('@/views/InterfacesView.vue'), meta: { title: '接口管理', roles: ['ADMIN', 'OPERATOR'] } },
    { path: '/datasources', name: 'datasources', component: () => import('@/views/DataSourcesView.vue'), meta: { title: '数据源管理', roles: ['ADMIN', 'OPERATOR'] } },
    { path: '/sql-apis', name: 'sql-apis', component: () => import('@/views/SqlApisView.vue'), meta: { title: 'SQL 查询接口', roles: ['ADMIN', 'OPERATOR'] } },
    { path: '/systems', name: 'systems', component: () => import('@/views/SystemsView.vue'), meta: { title: '系统管理', roles: ['ADMIN', 'OPERATOR'] } },
    { path: '/logs', name: 'logs', component: () => import('@/views/LogsView.vue'), meta: { title: '调用日志' } },
    { path: '/clients', name: 'clients', component: () => import('@/views/ClientsView.vue'), meta: { title: '调用方管理', roles: ['ADMIN'] } },
    { path: '/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
    { path: '/guide', name: 'guide', component: () => import('@/views/InvocationGuideView.vue'), meta: { title: '调用指南' } },
    { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '系统设置', roles: ['ADMIN'] } },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  await auth.initialize()

  if (to.meta.public) return auth.authenticated ? { path: '/dashboard' } : true
  if (!auth.authenticated) return { path: '/login', query: { redirect: to.fullPath } }

  const roles = allowedRoles(to)
  if (roles && (!auth.role || !roles.includes(auth.role))) return { path: '/dashboard', query: { forbidden: '1' } }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '接口平台')} · Interface Hub`
})

export default router
