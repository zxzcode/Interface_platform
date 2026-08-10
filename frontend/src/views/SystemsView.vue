<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Monitor, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { platformApi, type SystemCommand, type SystemOption } from '@/api/platform'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const systems = ref<SystemOption[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<SystemCommand>(emptyForm())
const filtered = computed(() => systems.value.filter(item => !keyword.value
  || `${item.code}${item.name}${item.baseUrl ?? ''}`.toLowerCase().includes(keyword.value.toLowerCase())))

function emptyForm(): SystemCommand {
  return { code: '', name: '', baseUrl: '', status: 'UNKNOWN' }
}

async function load(): Promise<void> {
  loading.value = true
  try { systems.value = await platformApi.systems() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '系统列表加载失败') }
  finally { loading.value = false }
}

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: SystemOption): void {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, baseUrl: row.baseUrl, status: row.status })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.code || !form.name || !form.baseUrl) {
    ElMessage.warning('请填写系统名称、编码和基础地址')
    return
  }
  if (!/^https?:\/\//i.test(form.baseUrl)) {
    ElMessage.warning('基础地址必须以 http:// 或 https:// 开头')
    return
  }
  saving.value = true
  try {
    if (editingId.value) await platformApi.updateSystem(editingId.value, { ...form })
    else await platformApi.createSystem({ ...form })
    ElMessage.success(editingId.value ? '系统已更新' : '系统已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '系统保存失败') }
  finally { saving.value = false }
}

async function remove(row: SystemOption): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除系统“${row.name}”吗？被接口引用时服务端会拒绝删除。`, '删除系统', { type: 'warning' })
    await platformApi.deleteSystem(row.id)
    ElMessage.success('系统已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="SYSTEM CATALOG" title="系统管理" description="维护 WMS、SAP 等来源与目标系统，基础地址同时作为 HTTP 转发目标边界。">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增系统</el-button>
    </PageHeader>
    <div class="security-notice"><el-icon><Monitor /></el-icon><div><strong>目标地址约束</strong><span>接口目标地址应归属于这里维护的系统基础地址，避免把平台配置成任意地址代理。</span></div></div>
    <section class="panel filter-panel"><div class="filter-row"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索系统名称、编码或基础地址" class="search-input" /><div class="filter-summary">共 <b>{{ filtered.length }}</b> 个系统</div></div></section>
    <section v-loading="loading" class="panel table-panel">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="系统" min-width="220"><template #default="scope"><div class="primary-cell"><div class="system-symbol">{{ scope.row.code.slice(0, 2) }}</div><div><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }}</small></div></div></template></el-table-column>
        <el-table-column label="基础地址" min-width="300"><template #default="scope"><code class="path-code break-value">{{ scope.row.baseUrl || '未配置' }}</code></template></el-table-column>
        <el-table-column label="连接状态" width="130"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button :icon="Edit" link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button :icon="Delete" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑系统' : '新增系统'" width="650px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="系统名称" required><el-input v-model.trim="form.name" placeholder="例如：SAP ERP" /></el-form-item><el-form-item label="系统编码" required><el-input v-model.trim="form.code" :disabled="Boolean(editingId)" placeholder="SAP" /></el-form-item></div>
        <el-form-item label="基础地址" required><el-input v-model.trim="form.baseUrl" placeholder="http://sap.internal:8000" /><small class="field-tip">填写协议、主机和端口，不建议包含具体接口路径</small></el-form-item>
        <el-form-item label="当前状态"><el-select v-model="form.status" style="width:100%"><el-option label="待检测" value="UNKNOWN" /><el-option label="运行正常" value="ONLINE" /><el-option label="性能波动" value="DEGRADED" /><el-option label="离线" value="OFFLINE" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存系统</el-button></template>
    </el-dialog>
  </div>
</template>
