<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, Coin, Connection, DataAnalysis, DataLine, Document, Expand, Fold, Guide, Key,
  Monitor, Setting, SwitchButton, UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { PlatformRole } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const sidebarOpen = ref(false)
const collapsed = ref(false)

const menu: Array<{ path: string; label: string; caption: string; icon: typeof DataAnalysis; roles?: PlatformRole[] }> = [
  { path: '/dashboard', label: '运行总览', caption: 'Overview', icon: DataAnalysis },
  { path: '/interfaces', label: '接口管理', caption: 'Interfaces', icon: Connection, roles: ['ADMIN', 'OPERATOR'] },
  { path: '/datasources', label: '数据源管理', caption: 'Data sources', icon: Coin, roles: ['ADMIN', 'OPERATOR'] },
  { path: '/sql-apis', label: 'SQL 查询接口', caption: 'Controlled query', icon: DataLine, roles: ['ADMIN', 'OPERATOR'] },
  { path: '/systems', label: '系统管理', caption: 'System catalog', icon: Monitor, roles: ['ADMIN', 'OPERATOR'] },
  { path: '/logs', label: '调用日志', caption: 'Observability', icon: Document },
  { path: '/clients', label: '调用方管理', caption: 'App credentials', icon: Key, roles: ['ADMIN'] },
  { path: '/users', label: '用户管理', caption: 'Access control', icon: UserFilled, roles: ['ADMIN'] },
  { path: '/guide', label: '调用指南', caption: 'Integration guide', icon: Guide },
  { path: '/settings', label: '系统设置', caption: 'Administration', icon: Setting, roles: ['ADMIN'] },
]

const visibleMenu = computed(() => menu.filter(item => !item.roles || (auth.role && item.roles.includes(auth.role))))
const currentTitle = computed(() => String(route.meta.title ?? '接口平台'))
const isPublicPage = computed(() => Boolean(route.meta.public))
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '平台用户')
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => ({ ADMIN: '系统管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }[auth.role ?? 'VIEWER']))

async function logout(): Promise<void> {
  await auth.logout()
  ElMessage.success('已安全退出')
  await router.replace('/login')
}

function handleUnauthorized(): void {
  auth.clearSession()
  ElMessage.warning('登录状态已失效，请重新登录')
  void router.replace({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(() => window.addEventListener('auth:unauthorized', handleUnauthorized))
onBeforeUnmount(() => window.removeEventListener('auth:unauthorized', handleUnauthorized))
</script>

<template>
  <router-view v-if="isPublicPage" />
  <div v-else class="app-shell" :class="{ 'is-collapsed': collapsed, 'is-mobile-open': sidebarOpen }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand__mark"><span>IF</span></div>
        <div class="brand__copy"><strong>Interface Hub</strong><small>企业接口平台</small></div>
      </div>

      <div class="sidebar__section-label">工作台</div>
      <nav class="nav-list">
        <router-link v-for="item in visibleMenu" :key="item.path" :to="item.path" class="nav-item" @click="sidebarOpen = false">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="nav-item__copy"><b>{{ item.label }}</b><small>{{ item.caption }}</small></span>
        </router-link>
      </nav>

      <div class="sidebar__footer">
        <div class="environment-card"><span class="pulse-dot" /><div><small>当前环境</small><strong>生产环境 · PROD</strong></div></div>
        <button class="collapse-button" type="button" @click="collapsed = !collapsed"><el-icon><component :is="collapsed ? Expand : Fold" /></el-icon><span>收起导航</span></button>
      </div>
    </aside>

    <div class="mobile-mask" @click="sidebarOpen = false" />

    <section class="main-area">
      <header class="topbar">
        <div class="topbar__left">
          <button class="mobile-menu" type="button" @click="sidebarOpen = true"><el-icon><Expand /></el-icon></button>
          <div><span class="topbar__crumb">接口平台 /</span><strong>{{ currentTitle }}</strong></div>
        </div>
        <div class="topbar__right">
          <div class="platform-health"><span />平台运行正常</div>
          <button class="icon-button" type="button" aria-label="通知"><el-icon><Bell /></el-icon></button>
          <el-dropdown trigger="click">
            <div class="user-block" tabindex="0"><div class="avatar">{{ avatarText }}</div><div><strong>{{ displayName }}</strong><small>{{ roleLabel }}</small></div></div>
            <template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{ auth.user?.username }}</el-dropdown-item><el-dropdown-item :icon="SwitchButton" divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>

      <main class="page-content">
        <router-view v-slot="{ Component }"><transition name="page" mode="out-in"><component :is="Component" /></transition></router-view>
      </main>
    </section>
  </div>
</template>
