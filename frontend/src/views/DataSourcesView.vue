<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Coin, Delete, Edit, Plus, Refresh, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { platformApi, type DataSourceCommand, type DataSourceSummary } from '@/api/platform'

const loading = ref(true)
const saving = ref(false)
const sources = ref<DataSourceSummary[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive<DataSourceCommand>(emptyForm())

function emptyForm(): DataSourceCommand {
  return { code: '', name: '', dbType: 'MYSQL', jdbcUrl: 'jdbc:mysql://127.0.0.1:3306/', driverClassName: '', username: '', password: '', enabled: true }
}

async function load() {
  loading.value = true
  try { sources.value = await platformApi.datasources() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '数据源加载失败') }
  finally { loading.value = false }
}

function openCreate() { editingId.value = undefined; Object.assign(form, emptyForm()); dialogVisible.value = true }
function openEdit(source: DataSourceSummary) {
  editingId.value = source.id
  Object.assign(form, { code: source.code, name: source.name, dbType: source.dbType, jdbcUrl: source.jdbcUrl,
    driverClassName: source.driverClassName, username: '', password: '', enabled: source.enabled })
  dialogVisible.value = true
}

async function save() {
  if (!form.code || !form.name || !form.jdbcUrl || (!editingId.value && (!form.username || !form.password))) return ElMessage.warning('请完整填写连接配置和只读账号')
  saving.value = true
  try {
    const saved = editingId.value ? await platformApi.updateDatasource(editingId.value, { ...form }) : await platformApi.createDatasource({ ...form })
    dialogVisible.value = false
    ElMessage.success('数据源已保存，正在测试连接')
    await load()
    await testConnection(saved)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '数据源保存失败') }
  finally { saving.value = false }
}

async function testConnection(source: DataSourceSummary) {
  try {
    const result = await platformApi.testDatasource(source.id)
    ElMessage.success(`${source.name} 连接成功，${result.durationMs} ms`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '连接测试失败') }
  finally { await load() }
}

async function detectAll() {
  const enabled = sources.value.filter(item => item.enabled)
  if (!enabled.length) return ElMessage.info('没有已启用的数据源')
  loading.value = true
  await Promise.allSettled(enabled.map(item => platformApi.testDatasource(item.id)))
  await load()
  ElMessage.success('数据源检测完成')
}

async function remove(source: DataSourceSummary) {
  try {
    await ElMessageBox.confirm(`确定删除数据源“${source.name}”吗？已被 SQL API 使用时将无法删除。`, '删除数据源', { type: 'warning' })
    await platformApi.deleteDatasource(source.id)
    ElMessage.success('数据源已删除')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="DATA CONNECTIONS" title="数据源管理" description="连接凭证加密保存，运行查询时使用独立的只读小型连接池。">
      <el-button :icon="Refresh" @click="detectAll">检测全部</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新增数据源</el-button>
    </PageHeader>
    <div class="security-notice"><el-icon><Warning /></el-icon><div><strong>安全基线</strong><span>这里配置的是业务只读库；平台基础库和业务查询库相互独立，调用方不能提交原始 SQL。</span></div></div>
    <el-empty v-if="!loading && !sources.length" description="还没有业务数据源，点击右上角新增" />
    <section v-else v-loading="loading" class="source-grid">
      <article v-for="source in sources" :key="source.id" class="source-card">
        <div class="source-card__header"><div class="database-icon"><el-icon><Coin /></el-icon></div><StatusBadge :status="source.status" /></div>
        <h2>{{ source.name }}</h2><p>{{ source.dbType }} · {{ source.code }}</p>
        <dl><div><dt>JDBC 地址</dt><dd class="truncate-value" :title="source.jdbcUrl">{{ source.jdbcUrl }}</dd></div><div><dt>凭证状态</dt><dd>{{ source.credentialConfigured ? '已加密配置' : '未配置' }}</dd></div></dl>
        <div class="source-card__footer"><small>{{ source.lastCheckedAt ? `最近检测 ${new Date(source.lastCheckedAt).toLocaleString('zh-CN', { hour12:false })}` : '尚未检测' }}</small><div><el-button link @click="testConnection(source)">测试</el-button><el-button :icon="Edit" link type="primary" @click="openEdit(source)">配置</el-button><el-button :icon="Delete" link type="danger" @click="remove(source)">删除</el-button></div></div>
      </article>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑数据源' : '新增数据源'" width="720px" destroy-on-close>
      <el-form label-position="top" class="dialog-form">
        <div class="form-grid"><el-form-item label="数据源名称" required><el-input v-model="form.name" placeholder="例如：WMS生产库" /></el-form-item><el-form-item label="数据源编码" required><el-input v-model="form.code" placeholder="WMS_PROD" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="数据库类型" required><el-select v-model="form.dbType" style="width:100%"><el-option label="MySQL" value="MYSQL" /><el-option label="SQL Server" value="SQL_SERVER" /><el-option label="PostgreSQL" value="POSTGRESQL" /><el-option label="H2（开发测试）" value="H2" /></el-select></el-form-item><el-form-item label="驱动类（可选）"><el-input v-model="form.driverClassName" placeholder="留空自动选择" /></el-form-item></div>
        <el-form-item label="JDBC 地址" required><el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:3306/database" /></el-form-item>
        <div class="form-grid"><el-form-item :label="editingId ? '只读用户名（留空保持不变）' : '只读用户名'" :required="!editingId"><el-input v-model="form.username" autocomplete="off" /></el-form-item><el-form-item :label="editingId ? '密码（留空保持不变）' : '密码'" :required="!editingId"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item></div>
        <div class="form-switch-row"><div><strong>启用数据源</strong><small>停用后关联的 SQL API 无法执行</small></div><el-switch v-model="form.enabled" /></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并测试</el-button></template>
    </el-dialog>
  </div>
</template>
