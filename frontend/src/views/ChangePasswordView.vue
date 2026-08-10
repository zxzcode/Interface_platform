<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { platformApi } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

function isValidPassword(value: string): boolean {
  return value.length >= 8 && value.length <= 72 && !/^[A-Za-z]+$/.test(value) && !/^\d+$/.test(value)
}

const rules: FormRules<typeof form> = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ validator: (_rule, value, callback) => callback(isValidPassword(value) ? undefined : new Error('密码需为 8-72 位，且不能是纯字母或纯数字')), trigger: 'blur' }],
  confirmPassword: [{ validator: (_rule, value, callback) => callback(value === form.newPassword ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }],
}

async function save(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    await platformApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    auth.clearSession()
    ElMessage.success('密码已修改，请使用新密码重新登录')
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码修改失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="view-stack">
    <PageHeader eyebrow="ACCOUNT SECURITY" title="修改密码" description="密码修改成功后，当前账号的全部登录 Token 将立即失效。" />
    <section class="panel" style="max-width: 640px; padding: 24px;">
      <el-alert title="请勿复用业务系统密码" description="新密码只会通过加密连接提交，平台不会回显或保留明文密码。" type="warning" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="dialog-form" style="margin-top: 20px;">
        <el-form-item label="当前密码" prop="currentPassword"><el-input v-model="form.currentPassword" :prefix-icon="Lock" type="password" autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" :prefix-icon="Lock" type="password" autocomplete="new-password" /><small class="field-tip">8-72 位，且不能是纯字母或纯数字。</small></el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" :prefix-icon="Lock" type="password" autocomplete="new-password" /></el-form-item>
        <div class="form-actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并重新登录</el-button></div>
      </el-form>
    </section>
  </div>
</template>
