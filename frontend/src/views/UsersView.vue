<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi, type PlatformRole, type UserAccount, type UserCommand } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const users = ref<UserAccount[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<UserCommand>(emptyForm())

const filtered = computed(() => users.value.filter(item => !keyword.value
  || `${item.username}${item.displayName}`.toLowerCase().includes(keyword.value.toLowerCase())))
const roleLabels: Record<PlatformRole, string> = { ADMIN: '系统管理员', OPERATOR: '运维人员', VIEWER: '只读用户' }

function emptyForm(): UserCommand {
  return { username: '', displayName: '', role: 'VIEWER', enabled: true, password: '' }
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
  dialogVisible.value = true
}

function openEdit(row: UserAccount): void {
  editingId.value = row.id
  Object.assign(form, { username: row.username, displayName: row.displayName, role: row.role, enabled: row.enabled, password: '' })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.username || !form.displayName || (!editingId.value && !form.password)) {
    ElMessage.warning('请完整填写用户信息和初始密码')
    return
  }
  if (form.password && form.password.length < 8) {
    ElMessage.warning('密码至少 8 位')
    return
  }
  saving.value = true
  try {
    const command: UserCommand = { ...form, password: form.password || undefined }
    if (editingId.value) await platformApi.updateUser(editingId.value, command)
    else await platformApi.createUser(command)
    ElMessage.success(editingId.value ? '用户已更新' : '用户已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '用户保存失败') }
  finally { saving.value = false }
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
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button :icon="Edit" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button :icon="Delete" link type="danger" :disabled="scope.row.id === auth.user?.id" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="620px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="用户名" required><el-input v-model.trim="form.username" :disabled="Boolean(editingId)" autocomplete="off" /></el-form-item><el-form-item label="显示名称" required><el-input v-model.trim="form.displayName" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="角色" required><el-select v-model="form.role" style="width:100%"><el-option v-for="(label, value) in roleLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item :label="editingId ? '新密码（留空不修改）' : '初始密码'" :required="!editingId"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /><small class="field-tip">至少 8 位；密码仅提交给服务端加密保存</small></el-form-item></div>
        <div class="form-switch-row"><div><strong>允许登录</strong><small>停用后该账号现有 Token 将由服务端失效</small></div><el-switch v-model="form.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存用户</el-button></template>
    </el-dialog>
  </div>
</template>
