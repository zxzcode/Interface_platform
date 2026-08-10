<script setup lang="ts">
import { Key, Lock, Monitor, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'

const save = () => ElMessage.success('设置已保存')
</script>

<template>
  <div class="view-stack settings-view">
    <PageHeader eyebrow="ADMINISTRATION" title="系统设置" description="管理平台基础参数、访问凭证与日志保留策略。" />

    <section class="settings-layout">
      <nav class="settings-nav panel">
        <button class="is-active"><el-icon><Setting /></el-icon><span><b>基础设置</b><small>平台名称与运行参数</small></span></button>
        <button><el-icon><Key /></el-icon><span><b>调用凭证</b><small>AppKey 与 Secret</small></span></button>
        <button><el-icon><Lock /></el-icon><span><b>用户与权限</b><small>平台管理权限</small></span></button>
        <button><el-icon><Monitor /></el-icon><span><b>监控设置</b><small>日志与告警阈值</small></span></button>
      </nav>

      <article class="panel settings-form">
        <div class="panel__header"><div><h2>基础设置</h2><p>这些配置将在下一次应用启动时生效。</p></div></div>
        <el-form label-position="top">
          <el-form-item label="平台名称"><el-input model-value="企业接口平台" /></el-form-item>
          <div class="form-grid">
            <el-form-item label="默认请求超时"><el-input model-value="15"><template #append>秒</template></el-input></el-form-item>
            <el-form-item label="单次 SQL 最大行数"><el-input model-value="1000"><template #append>行</template></el-input></el-form-item>
          </div>
          <div class="form-grid">
            <el-form-item label="调用日志保留"><el-select model-value="90"><el-option label="30 天" value="30" /><el-option label="90 天" value="90" /><el-option label="180 天" value="180" /></el-select></el-form-item>
            <el-form-item label="日志请求体"><el-select model-value="summary"><el-option label="仅保存脱敏摘要" value="summary" /><el-option label="不保存" value="none" /></el-select></el-form-item>
          </div>
          <el-form-item label="开放接口统一前缀"><el-input model-value="/open-api" disabled /></el-form-item>
          <div class="form-switch-row"><div><strong>记录响应摘要</strong><small>自动脱敏密码、Token、手机号等敏感字段</small></div><el-switch :model-value="true" /></div>
          <div class="form-switch-row"><div><strong>启用接口健康检测</strong><small>每 5 分钟检测目标系统连通性</small></div><el-switch :model-value="true" /></div>
          <div class="form-actions"><el-button type="primary" @click="save">保存设置</el-button></div>
        </el-form>
      </article>
    </section>
  </div>
</template>

