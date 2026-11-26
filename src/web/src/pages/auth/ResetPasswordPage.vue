<script setup lang="ts">
import { reactive, ref } from 'vue'

import type { AuthRequest } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import { passwordLengthHint, validateNewPassword } from '@/components/auth/formValidation'

const fields = reactive<Omit<AuthRequest<'resetPassword'>, 'challengeId'>>({ code: '', newPassword: '' })
const confirmation = ref('')
const passwordIssue = ref<ReturnType<typeof validateNewPassword>>(null)
const notice = ref('')

function previewReset() {
  passwordIssue.value = validateNewPassword(fields.newPassword, confirmation.value)
  notice.value = passwordIssue.value ? '' : '输入格式已通过检查。密码重置服务尚未接入。'
}

function clearFeedback() {
  passwordIssue.value = null
  notice.value = ''
}
</script>

<template>
  <h1 id="auth-heading">重置密码</h1>
  <form class="auth-form" @submit.prevent="previewReset" @input="clearFeedback">
    <div class="auth-field">
      <label for="reset-code">邮箱验证码</label>
      <input
        id="reset-code"
        v-model="fields.code"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]{8}"
        maxlength="8"
        aria-describedby="reset-code-help"
        required
      >
      <p id="reset-code-help" class="auth-hint">填写 8 位数字。静态预览可使用演示数字。</p>
    </div>
    <PasswordField
      v-model="fields.newPassword"
      id="reset-password"
      label="新密码"
      autocomplete="new-password"
      :hint="passwordLengthHint"
      :error="passwordIssue?.field === 'password' ? passwordIssue.message : undefined"
    />
    <PasswordField
      v-model="confirmation"
      id="reset-confirmation"
      label="确认密码"
      autocomplete="new-password"
      :error="passwordIssue?.field === 'confirmation' ? passwordIssue.message : undefined"
    />
    <button class="primary" type="submit">重置密码</button>
    <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
  </form>
  <div class="auth-links">
    <RouterLink :to="{ name: 'forgot-password' }">返回发送验证码</RouterLink>
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
  </div>
</template>
