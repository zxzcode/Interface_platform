<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, CircleCheck, Connection, DataLine, Refresh, Stopwatch, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { platformApi, type DashboardSummary, type InvocationLog } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const loading = ref(true)
const dashboard = ref<DashboardSummary>()
const recentLogs = ref<InvocationLog[]>([])
const auth = useAuthStore()

const maxCalls = computed(() => Math.max(...(dashboard.value?.trend.map((item) => item.total) ?? [1])))

const metrics = computed(() => [
  { label: '今日调用量', value: (dashboard.value?.todayCalls ?? 0).toLocaleString(), unit: '次', icon: Connection, tone: 'blue', detail: '较昨日同期 +12.6%' },
  { label: '调用成功率', value: `${dashboard.value?.successRate ?? 0}`, unit: '%', icon: CircleCheck, tone: 'green', detail: '目标值 ≥ 99.5%' },
  { label: '失败调用', value: dashboard.value?.failedCalls ?? 0, unit: '次', icon: WarningFilled, tone: 'orange', detail: '需关注 1 个超时接口' },
  { label: '平均响应时间', value: dashboard.value?.averageDurationMs ?? 0, unit: 'ms', icon: Stopwatch, tone: 'purple', detail: 'P95 响应 842ms' },
])

async function load() {
  loading.value = true
  try {
    const [summary, logs] = await Promise.all([platformApi.dashboard(), platformApi.logs(5)])
    dashboard.value = summary
    recentLogs.value = logs
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载运行数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="view-stack">
    <PageHeader eyebrow="OPERATIONS CENTER" title="运行总览" description="集中查看跨系统接口调用、链路健康状态与异常趋势。">
      <el-button :icon="Refresh" @click="load">刷新数据</el-button>
      <el-button v-if="auth.canOperate" type="primary" :icon="Connection" @click="$router.push('/interfaces')">管理接口</el-button>
    </PageHeader>

    <section class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-card__icon" :class="`is-${metric.tone}`"><el-icon><component :is="metric.icon" /></el-icon></div>
        <div class="metric-card__body">
          <span>{{ metric.label }}</span>
          <div><strong>{{ metric.value }}</strong><small>{{ metric.unit }}</small></div>
          <p>{{ metric.detail }}</p>
        </div>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="panel trend-panel">
        <div class="panel__header">
          <div><h2>调用趋势</h2><p>今日各时段接口调用量与成功量</p></div>
          <div class="legend"><span class="is-total" />总调用量 <span class="is-success" />成功调用</div>
        </div>
        <div class="trend-chart">
          <div v-for="point in dashboard?.trend" :key="point.time" class="trend-column">
            <div class="trend-column__bars">
              <div class="bar is-total" :style="{ height: `${Math.max(12, point.total / maxCalls * 100)}%` }"><span>{{ point.total }}</span></div>
              <div class="bar is-success" :style="{ height: `${Math.max(9, point.success / maxCalls * 100)}%` }" />
            </div>
            <small>{{ point.time }}</small>
          </div>
        </div>
      </article>

      <article class="panel system-panel">
        <div class="panel__header">
          <div><h2>系统连接状态</h2><p>核心业务系统实时连通性</p></div>
          <el-icon class="panel-icon"><DataLine /></el-icon>
        </div>
        <div class="system-list">
          <div v-for="system in dashboard?.systems" :key="system.code" class="system-row">
            <div class="system-symbol">{{ system.code.slice(0, 2) }}</div>
            <div class="system-row__copy"><strong>{{ system.name }}</strong><small>{{ system.code }} · 最近检测 1 分钟内</small></div>
            <StatusBadge :status="system.status" />
          </div>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel__header">
        <div><h2>最近调用</h2><p>最新跨系统请求执行情况</p></div>
        <el-button text type="primary" @click="$router.push('/logs')">查看全部 <el-icon><ArrowRight /></el-icon></el-button>
      </div>
      <el-table :data="recentLogs" class="clean-table">
        <el-table-column prop="requestTime" label="调用时间" width="180">
          <template #default="scope">{{ new Date(scope.row.requestTime).toLocaleString('zh-CN', { hour12: false }) }}</template>
        </el-table-column>
        <el-table-column prop="interfaceName" label="接口名称" min-width="180" />
        <el-table-column label="调用链路" min-width="190">
          <template #default="scope"><span class="route-text">{{ scope.row.caller }} <b>→</b> {{ scope.row.targetSystem }}</span></template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="185" />
        <el-table-column label="耗时" width="105"><template #default="scope">{{ scope.row.durationMs }} ms</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
      </el-table>
    </section>
  </div>
</template>
