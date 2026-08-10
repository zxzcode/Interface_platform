<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi, type DataSourceSummary, type SqlApiCommand, type SqlApiSummary, type SqlQueryResult } from '@/api/platform'

const loading = ref(true)
const saving = ref(false)
const apis = ref<SqlApiSummary[]>([])
const datasources = ref<DataSourceSummary[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const resultVisible = ref(false)
const editingId = ref<number>()
const result = ref<SqlQueryResult>()
const form = reactive<SqlApiCommand>(emptyForm())

const filtered = computed(() => apis.value.filter(item => !keyword.value || `${item.name}${item.code}${item.path}${item.datasourceName}`.toLowerCase().includes(keyword.value.toLowerCase())))

function emptyForm(): SqlApiCommand {
  return { code: '', name: '', description: '', path: '/open-api/sql/', method: 'POST', datasourceId: null,
    sql: 'select * from table_name where id = :id', timeoutSeconds: 10, maxRows: 1000, enabled: false }
}

async function load() {
  loading.value = true
  try { [apis.value, datasources.value] = await Promise.all([platformApi.sqlApis(), platformApi.datasources()]) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : 'SQL API 加载失败') }
  finally { loading.value = false }
}

function openCreate() { editingId.value = undefined; Object.assign(form, emptyForm()); dialogVisible.value = true }
function openEdit(row: SqlApiSummary) {
  editingId.value = row.id
  Object.assign(form, { code: row.code, name: row.name, description: row.description || '', path: row.path,
    method: row.method, datasourceId: row.datasourceId, sql: row.sql, timeoutSeconds: row.timeoutSeconds,
    maxRows: row.maxRows, enabled: row.enabled })
  dialogVisible.value = true
}

async function save() {
  if (!form.code || !form.name || !form.path || !form.datasourceId || !form.sql) return ElMessage.warning('请完整填写 SQL API 配置')
  saving.value = true
  try {
    if (editingId.value) await platformApi.updateSqlApi(editingId.value, { ...form })
    else await platformApi.createSqlApi({ ...form })
    ElMessage.success(editingId.value ? 'SQL API 已更新' : 'SQL API 已创建')
    dialogVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}

async function toggle(row: SqlApiSummary) {
  try { await platformApi.setSqlApiEnabled(row.id, row.enabled); ElMessage.success(row.enabled ? '查询接口已发布' : '查询接口已停用') }
  catch (error) { row.enabled = !row.enabled; ElMessage.error(error instanceof Error ? error.message : '状态更新失败') }
}

async function test(row: SqlApiSummary) {
  try {
    const prompt = await ElMessageBox.prompt('请输入命名参数 JSON，例如 {"id": 1}；无参数请输入 {}', '测试 SQL 查询', {
      inputValue: '{}', inputType: 'textarea', confirmButtonText: '执行查询', cancelButtonText: '取消',
      inputValidator: (value) => { try { const parsed = JSON.parse(value || '{}'); return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? true : '必须是 JSON 对象' } catch { return 'JSON 格式不正确' } },
    })
    result.value = await platformApi.testSqlApi(row.id, JSON.parse(prompt.value || '{}'))
    resultVisible.value = true
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '查询测试失败') }
}

async function remove(row: SqlApiSummary) {
  try {
    await ElMessageBox.confirm(`确定删除 SQL API“${row.name}”吗？`, '删除查询接口', { type: 'warning' })
    await platformApi.deleteSqlApi(row.id); ElMessage.success('SQL API 已删除'); await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="CONTROLLED QUERY" title="SQL 查询接口" description="管理员配置只读 SQL 模板，调用方只能传命名参数，不能提交 SQL。">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建查询接口</el-button>
    </PageHeader>
    <div class="security-notice"><span class="sql-shield">SELECT</span><div><strong>执行保护已启用</strong><span>拒绝注释、多语句和写操作；使用预编译参数，并强制执行超时及最大返回行数。</span></div></div>
    <section class="panel filter-panel"><div class="filter-row"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索名称、编码、路径或数据源" class="search-input" /><div class="filter-summary">共 <b>{{ filtered.length }}</b> 个查询接口</div></div></section>
    <section class="panel table-panel" v-loading="loading">
      <el-table :data="filtered" class="clean-table">
        <el-table-column label="查询接口" min-width="220"><template #default="scope"><div class="primary-cell"><div><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }}</small></div></div></template></el-table-column>
        <el-table-column prop="datasourceName" label="数据源" min-width="160" />
        <el-table-column label="开放地址" min-width="260"><template #default="scope"><code class="path-code"><em :class="`method-${scope.row.method.toLowerCase()}`">{{ scope.row.method }}</em>{{ scope.row.path }}</code></template></el-table-column>
        <el-table-column label="限制" width="145"><template #default="scope">{{ scope.row.maxRows }} 行 / {{ scope.row.timeoutSeconds }} 秒</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="scope"><el-switch v-model="scope.row.enabled" inline-prompt active-text="启" inactive-text="停" @change="toggle(scope.row)" /></template></el-table-column>
        <el-table-column label="操作" width="205" fixed="right"><template #default="scope"><el-button :icon="Edit" link type="primary" @click="openEdit(scope.row)">配置</el-button><el-button :icon="VideoPlay" link @click="test(scope.row)">测试</el-button><el-button :icon="Delete" link type="danger" @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 SQL 查询接口' : '新建 SQL 查询接口'" width="800px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="接口名称" required><el-input v-model="form.name" placeholder="多仓库存查询" /></el-form-item><el-form-item label="接口编码" required><el-input v-model="form.code" placeholder="WMS_MULTI_STOCK" /></el-form-item></div>
        <el-form-item label="开放路径" required><el-input v-model="form.path"><template #prepend><el-select v-model="form.method" style="width:105px"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></template></el-input></el-form-item>
        <el-form-item label="只读数据源" required><el-select v-model="form.datasourceId" style="width:100%" placeholder="请选择已配置的数据源"><el-option v-for="item in datasources" :key="item.id" :label="`${item.name} · ${item.status}`" :value="item.id" :disabled="!item.enabled" /></el-select></el-form-item>
        <el-form-item label="SELECT 模板" required><el-input v-model="form.sql" type="textarea" :rows="7" class="sql-editor" placeholder="select sku, qty from inventory where warehouse_id = :warehouseId" /><small class="field-tip">参数使用 :parameterName，占位值由预编译语句绑定。</small></el-form-item>
        <div class="form-grid"><el-form-item label="查询超时（秒）"><el-input-number v-model="form.timeoutSeconds" :min="1" :max="60" style="width:100%" /></el-form-item><el-form-item label="最大返回行数"><el-input-number v-model="form.maxRows" :min="1" :max="5000" style="width:100%" /></el-form-item></div>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <div class="form-switch-row"><div><strong>保存后立即发布</strong><small>发布后可通过开放路径执行查询</small></div><el-switch v-model="form.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存查询接口</el-button></template>
    </el-dialog>

    <el-drawer v-model="resultVisible" title="查询测试结果" size="620px"><template v-if="result"><el-alert :title="`查询成功，返回 ${result.rowCount} 行（上限 ${result.maxRows} 行）`" type="success" :closable="false" /><pre class="result-json">{{ JSON.stringify(result.rows, null, 2) }}</pre></template></el-drawer>
  </div>
</template>
