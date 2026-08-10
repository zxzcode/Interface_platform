<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CopyDocument, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { platformApi, type InvocationLog, type InvocationLogDetail } from '@/api/platform'

const loading = ref(true)
const logs = ref<InvocationLog[]>([])
const keyword = ref('')
const status = ref('all')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const selected = ref<InvocationLogDetail>()

const filtered = computed(() => logs.value.filter((item) => {
  const hit = !keyword.value || `${item.traceId}${item.interfaceName}${item.caller}`.toLowerCase().includes(keyword.value.toLowerCase())
  return hit && (status.value === 'all' || item.status === status.value)
}))

async function load() {
  loading.value = true
  try { logs.value = await platformApi.logs(100) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '日志加载失败') }
  finally { loading.value = false }
}

async function inspect(row: InvocationLog) {
  drawerVisible.value = true
  detailLoading.value = true
  try { selected.value = await platformApi.logDetail(row.traceId) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '日志详情加载失败') }
  finally { detailLoading.value = false }
}

async function copyTraceId() {
  if (!selected.value) return
  await navigator.clipboard.writeText(selected.value.traceId)
  ElMessage.success('Trace ID 已复制')
}

onMounted(load)
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="OBSERVABILITY" title="调用日志" description="按 Trace ID 追踪请求入口、目标系统、响应耗时与执行结果。">
      <el-button :icon="Refresh" @click="load">刷新日志</el-button>
    </PageHeader>

    <section class="panel filter-panel">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索 Trace ID、接口或调用方" class="search-input" />
        <el-select v-model="status" class="status-select">
          <el-option label="全部结果" value="all" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <div class="filter-summary">显示 <b>{{ filtered.length }}</b> 条记录</div>
      </div>
    </section>

    <section class="panel table-panel" v-loading="loading">
      <el-table :data="filtered" class="clean-table" @row-dblclick="inspect">
        <el-table-column label="调用时间" width="185"><template #default="scope">{{ new Date(scope.row.requestTime).toLocaleString('zh-CN', { hour12: false }) }}</template></el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="190"><template #default="scope"><code class="trace-code">{{ scope.row.traceId }}</code></template></el-table-column>
        <el-table-column label="接口名称" min-width="185"><template #default="scope"><div class="log-interface"><strong>{{ scope.row.interfaceName }}</strong><small>{{ scope.row.routeType }} · {{ scope.row.requestMethod }}</small></div></template></el-table-column>
        <el-table-column label="调用链路" min-width="190"><template #default="scope"><span class="route-text">{{ scope.row.caller }} <b>→</b> {{ scope.row.targetSystem }}</span></template></el-table-column>
        <el-table-column prop="httpStatus" label="HTTP" width="80" />
        <el-table-column label="耗时" width="105"><template #default="scope"><span :class="{ 'slow-text': scope.row.durationMs > 1000 }">{{ scope.row.durationMs }} ms</span></template></el-table-column>
        <el-table-column label="结果" width="115"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right"><template #default="scope"><el-button :icon="View" link type="primary" @click="inspect(scope.row)">详情</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="drawerVisible" title="调用详情" size="620px">
      <div v-loading="detailLoading">
      <template v-if="selected">
        <div class="trace-hero">
          <div><small>TRACE ID</small><strong>{{ selected.traceId }}</strong></div>
          <el-button :icon="CopyDocument" circle @click="copyTraceId" />
        </div>
        <div class="detail-section">
          <h3>执行概况</h3>
          <dl class="detail-list">
            <div><dt>接口名称</dt><dd>{{ selected.interfaceName }}</dd></div>
            <div><dt>路由类型</dt><dd>{{ selected.routeType }} · {{ selected.requestMethod }} {{ selected.requestPath }}</dd></div>
            <div><dt>调用链路</dt><dd>{{ selected.caller }} → {{ selected.targetSystem }}</dd></div>
            <div><dt>目标地址</dt><dd class="break-value">{{ selected.targetAddress || '-' }}</dd></div>
            <div><dt>HTTP 状态</dt><dd>{{ selected.httpStatus }}</dd></div>
            <div><dt>响应耗时</dt><dd>{{ selected.durationMs }} ms</dd></div>
            <div><dt>执行结果</dt><dd><StatusBadge :status="selected.status" /></dd></div>
          </dl>
        </div>
        <div v-if="selected.platformCode || selected.errorMessage" class="detail-section"><h3>异常信息</h3><pre>{{ selected.platformCode || '-' }}
{{ selected.errorMessage || '-' }}</pre></div>
        <div class="detail-section"><h3>请求头（已脱敏）</h3><pre>{{ selected.requestHeaders || '未记录' }}</pre></div>
        <div class="detail-section"><h3>请求摘要（已脱敏/截断）</h3><pre>{{ selected.requestSummary || '无请求体' }}</pre></div>
        <div class="detail-section"><h3>响应头（已脱敏）</h3><pre>{{ selected.responseHeaders || '未记录' }}</pre></div>
        <div class="detail-section"><h3>响应摘要（已脱敏/截断）</h3><pre>{{ selected.responseSummary || '无响应体' }}</pre></div>
      </template>
      </div>
    </el-drawer>
  </div>
</template>
