<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Connection, Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return
  submitting.value = true
  try {
    await auth.login({ ...form })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查账号和密码')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="login-brand"><span>IF</span><strong>Interface Hub</strong></div>
      <div class="login-hero__copy">
        <span class="login-kicker">LIGHTWEIGHT INTEGRATION PLATFORM</span>
        <h1>让跨系统接口<br>可管理、可追踪、可控制</h1>
        <p>统一管理 HTTP 转发、SAP 调用与多数据源只读查询，以 Trace ID 贯穿每一次调用。</p>
      </div>
      <div class="login-capabilities">
        <span><el-icon><Connection /></el-icon>统一接口入口</span>
        <span><el-icon><Lock /></el-icon>签名与权限控制</span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-card__heading">
          <div class="brand__mark"><span>IF</span></div>
          <div><h2>登录接口平台</h2><p>使用平台管理账号继续</p></div>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="form.username" :prefix-icon="User" size="large" autocomplete="username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" size="large" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" :loading="submitting" @click="submit">登录平台</el-button>
        </el-form>
        <div class="login-security"><el-icon><Lock /></el-icon><span>登录凭证通过加密通道传输，Token 不写入业务日志。</span></div>
      </div>
    </section>
  </main>
</template>
