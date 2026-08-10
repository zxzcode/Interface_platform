<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

const label = computed(() => ({
  ONLINE: '运行正常',
  SUCCESS: '成功',
  DEGRADED: '性能波动',
  FAILED: '失败',
  OFFLINE: '离线',
  RUNNING: '执行中',
  UNKNOWN: '待检测',
}[props.status] ?? props.status))

const tone = computed(() => {
  if (['ONLINE', 'SUCCESS'].includes(props.status)) return 'success'
  if (['DEGRADED', 'RUNNING'].includes(props.status)) return 'warning'
  if (['FAILED', 'OFFLINE'].includes(props.status)) return 'danger'
  return 'neutral'
})
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <i />{{ label }}
  </span>
</template>

