<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CopyDocument, Delete, Edit, Key, Lock, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi, type ApiClientSummary, type ClientPermission, type CreateApiClientCommand, type InterfaceSummary, type SqlApiSummary } from '@/api/platform'

const loading = ref(false)
const saving = ref(false)
const clients = ref<ApiClientSummary[]>([])
const interfaces = ref<InterfaceSummary[]>([])
const sqlApis = ref<SqlApiSummary[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<CreateApiClientCommand>(emptyForm())
const secretVisible = ref(false)
const oneTimeSecret = reactive({ appKey: '', appSecret: '' })
const permissionVisible = ref(false)
const permissionSaving = ref(false)
const permissionLoading = ref(false)
const selectedClient = ref<ApiClientSummary>()
const checkedPermissions = ref<string[]>([])

const filtered = computed(() => clients.value.filter(item => !keyword.value
  || `${item.code}${item.name}${item.appKey}`.toLowerCase().includes(keyword.value.toLowerCase())))
const httpResources = computed(() => interfaces.value.map(item => ({ key: `HTTP:${item.code}`, label: `${item.name} · ${item.method} ${item.path}`, enabled: item.enabled })))
const sqlResources = computed(() => sqlApis.value.map(item => ({ key: `SQL:${item.code}`, label: `${item.name} · ${item.method} ${item.path}`, enabled: item.enabled })))

function emptyForm(): CreateApiClientCommand { return { code: '', name: '', enabled: true, permissions: [] } }

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await Promise.all([platformApi.clients(), platformApi.interfaces(), platformApi.sqlApis()])
    clients.value = result[0]
    interfaces.value = result[1]
    sqlApis.value = result[2]
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '调用方数据加载失败') }
  finally { loading.value = false }
}

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: ApiClientSummary): void {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, enabled: row.enabled, permissions: row.permissions })
  dialogVisible.value = true
}

function showSecret(appKey: string, appSecret: string): void {
  oneTimeSecret.appKey = appKey
  oneTimeSecret.appSecret = appSecret
  secretVisible.value = true
}

async function save(): Promise<void> {
  if (!form.code || !form.name) {
    ElMessage.warning('请输入调用方编码和名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      const current = clients.value.find(item => item.id === editingId.value)
      await platformApi.updateClient(editingId.value, { name: form.name, enabled: form.enabled, permissions: current?.permissions ?? [] })
      ElMessage.success('调用方已更新')
    } else {
      const result = await platformApi.createClient({ ...form })
      showSecret(result.client.appKey, result.appSecret)
      ElMessage.success('调用方已创建，请立即保存 AppSecret')
    }
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '调用方保存失败') }
  finally { saving.value = false }
}

async function rotateSecret(row: ApiClientSummary): Promise<void> {
  try {
    await ElMessageBox.confirm('轮换后原 AppSecret 立即失效，调用系统必须同步更新。确定继续吗？', '轮换 AppSecret', { type: 'warning', confirmButtonText: '确认轮换' })
    const result = await platformApi.rotateClientSecret(row.id)
    showSecret(result.client.appKey, result.appSecret)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : 'Secret 轮换失败')
  }
}

async function openPermissions(row: ApiClientSummary): Promise<void> {
  selectedClient.value = row
  permissionVisible.value = true
  permissionLoading.value = true
  try {
    checkedPermissions.value = row.permissions.map(item => `${item.routeType}:${item.resourceCode}`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限加载失败') }
  finally { permissionLoading.value = false }
}

async function savePermissions(): Promise<void> {
  if (!selectedClient.value) return
  permissionSaving.value = true
  try {
    const permissions: ClientPermission[] = checkedPermissions.value.map((value) => {
      const separator = value.indexOf(':')
      return { routeType: value.slice(0, separator) as ClientPermission['routeType'], resourceCode: value.slice(separator + 1) }
    })
    const saved = await platformApi.updateClient(selectedClient.value.id, {
      name: selectedClient.value.name,
      enabled: selectedClient.value.enabled,
      permissions,
    })
    clients.value = clients.value.map(item => item.id === saved.id ? saved : item)
    selectedClient.value = saved
    ElMessage.success('接口权限已保存')
    permissionVisible.value = false
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '权限保存失败') }
  finally { permissionSaving.value = false }
}

async function remove(row: ApiClientSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除调用方“${row.name}”吗？其 AppKey 将立即失效。`, '删除调用方', { type: 'warning' })
    await platformApi.deleteClient(row.id)
    ElMessage.success('调用方已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function copy(value: string, label: string): Promise<void> {
  await navigator.clipboard.writeText(value)
  ElMessage.success(`${label} 已复制`)
}

function clearSecret(): void {
  oneTimeSecret.appKey = ''
  oneTimeSecret.appSecret = ''
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="API CONSUMERS" title="调用方管理" description="为外部系统签发独立 AppKey，并按 HTTP 或 SQL 接口授予最小权限。">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增调用方</el-button>
    </PageHeader>
    <div class="security-notice"><el-icon><Lock /></el-icon><div><strong>AppSecret 只展示一次</strong><span>平台只在创建或轮换时返回 Secret；请通过安全渠道交付，列表中无法再次查看。</span></div></div>
    <section class="panel filter-panel"><div class="filter-row"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索调用方名称或 AppKey" class="search-input" /><div class="filter-summary">共 <b>{{ filtered.length }}</b> 个调用方</div></div></section>
    <section v-loading="loading" class="panel table-panel">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="调用方" min-width="220"><template #default="scope"><div class="primary-cell"><div class="primary-cell__icon"><el-icon><Key /></el-icon></div><div><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="AppKey" min-width="250"><template #default="scope"><code class="credential-code">{{ scope.row.appKey }}</code><el-button :icon="CopyDocument" link @click="copy(scope.row.appKey, 'AppKey')" /></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="scope"><span class="text-status" :class="scope.row.enabled ? 'is-enabled' : 'is-disabled'">{{ scope.row.enabled ? '● 已启用' : '○ 已停用' }}</span></template></el-table-column>
        <el-table-column label="更新时间" min-width="170"><template #default="scope">{{ scope.row.updatedAt ? new Date(scope.row.updatedAt).toLocaleString('zh-CN', { hour12:false }) : '-' }}</template></el-table-column>
        <el-table-column label="操作" width="265" fixed="right"><template #default="scope"><el-button :icon="Lock" link type="primary" @click="openPermissions(scope.row)">权限</el-button><el-button :icon="Refresh" link @click="rotateSecret(scope.row)">轮换 Secret</el-button><el-button :icon="Edit" link @click="openEdit(scope.row)">编辑</el-button><el-button :icon="Delete" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑调用方' : '新增调用方'" width="590px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="调用方名称" required><el-input v-model.trim="form.name" placeholder="例如：WMS 生产系统" /></el-form-item><el-form-item label="调用方编码" required><el-input v-model.trim="form.code" :disabled="Boolean(editingId)" placeholder="WMS_PROD" /></el-form-item></div>
        <div class="form-switch-row"><div><strong>启用调用凭证</strong><small>停用后该 AppKey 的所有开放接口请求都会被拒绝</small></div><el-switch v-model="form.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '创建并生成凭证' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" title="分配接口权限" width="720px" destroy-on-close>
      <div v-loading="permissionLoading" class="permission-editor">
        <el-alert :title="`调用方：${selectedClient?.name ?? ''}`" description="仅勾选业务确实需要调用的接口；接口自身停用时，即使已授权也不能调用。" type="info" :closable="false" show-icon />
        <el-checkbox-group v-model="checkedPermissions">
          <section class="permission-group"><h3>HTTP 转发接口</h3><el-checkbox v-for="item in httpResources" :key="item.key" :value="item.key"><span>{{ item.label }}</span><small>{{ item.enabled ? '已启用' : '接口已停用' }}</small></el-checkbox><el-empty v-if="!httpResources.length" description="暂无 HTTP 接口" :image-size="52" /></section>
          <section class="permission-group"><h3>SQL 查询接口</h3><el-checkbox v-for="item in sqlResources" :key="item.key" :value="item.key"><span>{{ item.label }}</span><small>{{ item.enabled ? '已发布' : '接口已停用' }}</small></el-checkbox><el-empty v-if="!sqlResources.length" description="暂无 SQL 接口" :image-size="52" /></section>
        </el-checkbox-group>
      </div>
      <template #footer><el-button @click="permissionVisible=false">取消</el-button><el-button type="primary" :loading="permissionSaving" @click="savePermissions">保存权限</el-button></template>
    </el-dialog>

    <el-dialog v-model="secretVisible" title="请立即保存调用凭证" width="640px" :close-on-click-modal="false" @closed="clearSecret">
      <el-alert title="AppSecret 关闭后不能再次查看" description="请复制到调用系统的安全配置或密钥管理服务中，不要发送到群聊、写入代码或提交到 Git。" type="warning" :closable="false" show-icon />
      <div class="secret-field"><span>AppKey</span><code>{{ oneTimeSecret.appKey }}</code><el-button :icon="CopyDocument" @click="copy(oneTimeSecret.appKey, 'AppKey')">复制</el-button></div>
      <div class="secret-field"><span>AppSecret</span><code>{{ oneTimeSecret.appSecret }}</code><el-button :icon="CopyDocument" type="primary" @click="copy(oneTimeSecret.appSecret, 'AppSecret')">复制</el-button></div>
      <template #footer><el-button type="primary" @click="secretVisible=false">我已安全保存</el-button></template>
    </el-dialog>
  </div>
</template>
