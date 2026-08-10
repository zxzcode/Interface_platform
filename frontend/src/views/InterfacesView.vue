<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Connection, Delete, Edit, Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi, type InterfaceCommand, type InterfaceSummary, type SystemOption } from '@/api/platform'

const loading = ref(true)
const router = useRouter()
const saving = ref(false)
const interfaces = ref<InterfaceSummary[]>([])
const systems = ref<SystemOption[]>([])
const keyword = ref('')
const status = ref('all')
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<InterfaceCommand>(emptyForm())

const filtered = computed(() => interfaces.value.filter((item) => {
  const matchesKeyword = !keyword.value || `${item.name}${item.code}${item.path}`.toLowerCase().includes(keyword.value.toLowerCase())
  return matchesKeyword && (status.value === 'all' || (status.value === 'enabled' ? item.enabled : !item.enabled))
}))

function emptyForm(): InterfaceCommand {
  return { code: '', name: '', description: '', sourceSystemId: null, targetSystemId: null, method: 'POST',
    path: '/open-api/', targetUrl: 'http://', connectTimeoutMs: 3000, readTimeoutMs: 15000, enabled: false }
}

async function load() {
  loading.value = true
  try { [interfaces.value, systems.value] = await Promise.all([platformApi.interfaces(), platformApi.systems()]) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '接口列表加载失败') }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: InterfaceSummary) {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, description: row.description || '',
    sourceSystemId: row.sourceSystemId, targetSystemId: row.targetSystemId, method: row.method,
    path: row.path, targetUrl: row.targetUrl, connectTimeoutMs: row.connectTimeoutMs,
    readTimeoutMs: row.readTimeoutMs, enabled: row.enabled })
  dialogVisible.value = true
}

async function save() {
  if (!form.name || !form.code || !form.path || !form.targetUrl || !form.sourceSystemId || !form.targetSystemId) {
    return ElMessage.warning('请完整填写接口、系统和目标地址')
  }
  saving.value = true
  try {
    if (editingId.value) await platformApi.updateInterface(editingId.value, { ...form })
    else await platformApi.createInterface({ ...form })
    ElMessage.success(editingId.value ? '接口配置已更新' : '接口已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '接口保存失败') }
  finally { saving.value = false }
}

async function toggle(row: InterfaceSummary) {
  try { await platformApi.setInterfaceEnabled(row.id, row.enabled); ElMessage.success(row.enabled ? '接口已启用' : '接口已停用') }
  catch (error) { row.enabled = !row.enabled; ElMessage.error(error instanceof Error ? error.message : '状态更新失败') }
}

async function remove(row: InterfaceSummary) {
  try {
    await ElMessageBox.confirm(`确定删除接口“${row.name}”吗？历史调用日志会保留。`, '删除接口', { type: 'warning' })
    await platformApi.deleteInterface(row.id)
    ElMessage.success('接口已删除')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

async function testInterface(row: InterfaceSummary) {
  await ElMessageBox.confirm(`开放接口“${row.name}”必须使用已授权 AppKey 完成签名后调用。是否打开调用指南？`, '安全调用提示', {
    type: 'info', confirmButtonText: '打开调用指南', cancelButtonText: '取消',
  }).then(() => router.push('/guide')).catch(() => undefined)
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="API CATALOG" title="接口管理" description="配置统一入口和目标地址，启用后立即按原始 HTTP 语义转发。">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建接口</el-button>
    </PageHeader>

    <section class="panel filter-panel"><div class="filter-row">
      <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索接口名称、编码或路径" class="search-input" />
      <el-select v-model="status" class="status-select"><el-option label="全部状态" value="all" /><el-option label="运行中" value="enabled" /><el-option label="已停用" value="disabled" /></el-select>
      <div class="filter-summary">共 <b>{{ filtered.length }}</b> 个接口</div>
    </div></section>

    <section class="panel table-panel" v-loading="loading">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="接口" min-width="230"><template #default="scope"><div class="primary-cell"><div class="primary-cell__icon"><el-icon><Connection /></el-icon></div><div><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="调用链路" min-width="190"><template #default="scope"><span class="route-text">{{ scope.row.sourceSystem }} <b>→</b> {{ scope.row.targetSystem }}</span></template></el-table-column>
        <el-table-column label="开放地址" min-width="260"><template #default="scope"><code class="path-code"><em :class="`method-${scope.row.method.toLowerCase()}`">{{ scope.row.method }}</em>{{ scope.row.path }}</code></template></el-table-column>
        <el-table-column label="今日调用" prop="todayCalls" width="90" />
        <el-table-column label="成功率" width="90"><template #default="scope"><b>{{ Number(scope.row.successRate).toFixed(2) }}%</b></template></el-table-column>
        <el-table-column label="平均耗时" width="100"><template #default="scope">{{ scope.row.averageDurationMs }} ms</template></el-table-column>
        <el-table-column label="状态" width="88"><template #default="scope"><el-switch v-model="scope.row.enabled" inline-prompt active-text="启" inactive-text="停" @change="toggle(scope.row)" /></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="scope"><el-button :icon="Edit" link type="primary" @click="openEdit(scope.row)">配置</el-button><el-button :icon="VideoPlay" link @click="testInterface(scope.row)">测试</el-button><el-button :icon="Delete" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 HTTP 接口' : '新建 HTTP 接口'" width="760px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="接口名称" required><el-input v-model="form.name" placeholder="例如：物料主数据查询" /></el-form-item><el-form-item label="接口编码" required><el-input v-model="form.code" placeholder="WMS_SAP_MATERIAL_QUERY" /></el-form-item></div>
        <el-form-item label="开放路径" required><el-input v-model="form.path"><template #prepend><el-select v-model="form.method" style="width:105px"><el-option v-for="method in ['GET','POST','PUT','PATCH','DELETE']" :key="method" :label="method" :value="method" /></el-select></template></el-input></el-form-item>
        <el-form-item label="目标完整地址" required><el-input v-model="form.targetUrl" placeholder="http://sap-server/api/material/query" /></el-form-item>
        <div class="form-grid"><el-form-item label="来源系统" required><el-select v-model="form.sourceSystemId" style="width:100%"><el-option v-for="item in systems" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="目标系统" required><el-select v-model="form.targetSystemId" style="width:100%"><el-option v-for="item in systems" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></div>
        <div class="form-grid"><el-form-item label="连接超时"><el-input-number v-model="form.connectTimeoutMs" :min="500" :max="30000" :step="500" style="width:100%" /><small class="field-tip">毫秒</small></el-form-item><el-form-item label="响应超时"><el-input-number v-model="form.readTimeoutMs" :min="500" :max="120000" :step="1000" style="width:100%" /><small class="field-tip">毫秒</small></el-form-item></div>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <div class="form-switch-row"><div><strong>保存后立即启用</strong><small>启用后外部系统可以立即访问该开放路径</small></div><el-switch v-model="form.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存接口</el-button></template>
    </el-dialog>
  </div>
</template>
