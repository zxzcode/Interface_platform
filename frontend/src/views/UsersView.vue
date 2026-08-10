<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Key, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi, type PlatformRole, type UserAccount } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const users = ref<UserAccount[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<UserForm>(emptyForm())
const initialPassword = ref('')

const filtered = computed(() => users.value.filter(item => !keyword.value
  || `${item.username}${item.displayName}`.toLowerCase().includes(keyword.value.toLowerCase())))
const roleLabels: Record<PlatformRole, string> = { ADMIN: '系统管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }

interface UserForm { username: string; displayName: string; role: PlatformRole; enabled: boolean }

function emptyForm(): UserForm {
  return { username: '', displayName: '', role: 'VIEWER', enabled: true }
}

async function load(): Promise<void> {
  loading.value = true
  try { users.value = await platformApi.users() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败') }
  finally { loading.value = false }
}

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  initialPassword.value = ''
  dialogVisible.value = true
}

function openEdit(row: UserAccount): void {
  editingId.value = row.id
  Object.assign(form, { username: row.username, displayName: row.displayName, role: row.role, enabled: row.enabled })
  initialPassword.value = ''
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.username || !form.displayName || (!editingId.value && !initialPassword.value)) {
    ElMessage.warning('请完整填写用户信息和初始密码')
    return
  }
  if (!editingId.value && !isValidPassword(initialPassword.value)) {
    ElMessage.warning('密码需为 8-72 位，且不能是纯字母或纯数字')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await platformApi.updateUser(editingId.value, { displayName: form.displayName, role: form.role, enabled: form.enabled })
      if (editingId.value === auth.user?.id) {
        auth.clearSession()
        ElMessage.success('个人资料已更新，请重新登录')
        dialogVisible.value = false
        await router.replace('/login')
        return
      }
    } else {
      await platformApi.createUser({ ...form, password: initialPassword.value })
    }
    ElMessage.success(editingId.value ? '用户已更新' : '用户已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '用户保存失败') }
  finally { saving.value = false }
}

function isValidPassword(value: string): boolean {
  return value.length >= 8 && value.length <= 72 && !/^[A-Za-z]+$/.test(value) && !/^\d+$/.test(value)
}

async function resetPassword(row: UserAccount): Promise<void> {
  if (row.id === auth.user?.id) {
    await router.push('/change-password')
    return
  }
  try {
    const result = await ElMessageBox.prompt(`为“${row.displayName}”设置新密码。`, '重置密码', {
      inputType: 'password', inputPlaceholder: '8-72 位，不能为纯字母或纯数字',
      inputValidator: value => isValidPassword(value) || '密码需为 8-72 位，且不能是纯字母或纯数字',
      confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning',
    })
    await platformApi.resetUserPassword(row.id, { password: result.value })
    ElMessage.success('密码已重置，原登录 Token 已失效')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '密码重置失败')
  }
}

async function remove(row: UserAccount): Promise<void> {
  if (row.id === auth.user?.id) {
    ElMessage.warning('不能删除当前登录账号')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除用户“${row.displayName}”吗？`, '删除用户', { type: 'warning' })
    await platformApi.deleteUser(row.id)
    ElMessage.success('用户已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="ACCESS CONTROL" title="用户管理" description="管理平台后台登录账号及角色权限，账号变更不会影响调用方 AppKey。">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </PageHeader>

    <section class="role-overview">
      <article><b>系统管理员</b><span>全部配置、用户和调用方权限</span></article>
      <article><b>运维人员</b><span>接口、数据源、SQL 与日志运维</span></article>
      <article><b>只读用户</b><span>运行总览、日志与调用指南</span></article>
    </section>

    <section class="panel filter-panel"><div class="filter-row">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索用户名或姓名" class="search-input" />
      <div class="filter-summary">共 <b>{{ filtered.length }}</b> 个用户</div>
    </div></section>

    <section v-loading="loading" class="panel table-panel">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="用户" min-width="210"><template #default="scope"><div class="primary-cell"><div class="primary-cell__icon"><el-icon><UserFilled /></el-icon></div><div><strong>{{ scope.row.displayName }}</strong><small>{{ scope.row.username }}</small></div></div></template></el-table-column>
        <el-table-column label="角色" width="150"><template #default="scope"><el-tag effect="plain">{{ roleLabels[scope.row.role as PlatformRole] }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="scope"><span class="text-status" :class="scope.row.enabled ? 'is-enabled' : 'is-disabled'">{{ scope.row.enabled ? '● 已启用' : '○ 已停用' }}</span></template></el-table-column>
        <el-table-column label="最近登录" min-width="180"><template #default="scope">{{ scope.row.lastLoginAt ? new Date(scope.row.lastLoginAt).toLocaleString('zh-CN', { hour12: false }) : '尚未登录' }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right"><template #default="scope"><el-button :icon="Edit" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button :icon="Key" link @click="resetPassword(scope.row)">{{ scope.row.id === auth.user?.id ? '修改密码' : '重置密码' }}</el-button><el-button :icon="Delete" link type="danger" :disabled="scope.row.id === auth.user?.id" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="620px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="用户名" required><el-input v-model.trim="form.username" :disabled="Boolean(editingId)" autocomplete="off" /></el-form-item><el-form-item label="显示名称" required><el-input v-model.trim="form.displayName" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="角色" required><el-select v-model="form.role" :disabled="editingId === auth.user?.id" style="width:100%"><el-option v-for="(label, value) in roleLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item v-if="!editingId" label="初始密码" required><el-input v-model="initialPassword" type="password" autocomplete="new-password" /><small class="field-tip">8-72 位，且不能为纯字母或纯数字；密码不会在列表中展示。</small></el-form-item></div>
        <div class="form-switch-row"><div><strong>允许登录</strong><small>停用后该账号现有 Token 将由服务端失效</small></div><el-switch v-model="form.enabled" :disabled="editingId === auth.user?.id" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存用户</el-button></template>
    </el-dialog>
  </div>
</template>
