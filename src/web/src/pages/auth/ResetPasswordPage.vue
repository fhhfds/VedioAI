<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { AuthApiError, resetPassword, type AuthRequest } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import { passwordLengthHint, validateNewPassword } from '@/components/auth/formValidation'

const route = useRoute()
const router = useRouter()
const challengeId = computed(() => typeof route.query.challengeId === 'string'
  ? route.query.challengeId
  : '')

const fields = reactive<Omit<AuthRequest<'resetPassword'>, 'challengeId'>>({
  code: '',
  newPassword: '',
})
const confirmation = ref('')
const passwordIssue = ref<ReturnType<typeof validateNewPassword>>(null)
const errorMessage = ref('')
const submitting = ref(false)

function clearFeedback() {
  passwordIssue.value = null
  errorMessage.value = ''
}

async function submitReset() {
  if (submitting.value) return

  errorMessage.value = ''
  passwordIssue.value = validateNewPassword(fields.newPassword, confirmation.value)
  if (passwordIssue.value) return

  if (!challengeId.value) {
    errorMessage.value = '缺少重置验证码，请重新申请。'
    return
  }

  submitting.value = true
  try {
    await resetPassword({
      challengeId: challengeId.value,
      code: fields.code,
      newPassword: fields.newPassword,
    })
    fields.newPassword = ''
    confirmation.value = ''
    await router.replace({ name: 'login', query: { passwordReset: '1' } })
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '密码重置失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <h1 id="auth-heading">重置密码</h1>
  <p v-if="!challengeId" class="auth-message auth-field-error" role="alert">
    缺少重置验证码，请先申请。
  </p>
  <form v-else class="auth-form" @submit.prevent="submitReset" @input="clearFeedback">
    <div class="auth-field">
      <label for="reset-code">邮箱验证码</label>
      <input
        id="reset-code"
        v-model="fields.code"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]{6}"
        maxlength="6"
        aria-describedby="reset-code-help"
        required
      >
      <p id="reset-code-help" class="auth-hint">填写邮件中的 6 位数字。</p>
    </div>
    <PasswordField
      v-model="fields.newPassword"
      id="reset-password"
      label="新密码"
      autocomplete="new-password"
      :maxlength="128"
      :hint="passwordLengthHint"
      :error="passwordIssue?.field === 'password' ? passwordIssue.message : undefined"
    />
    <PasswordField
      v-model="confirmation"
      id="reset-confirmation"
      label="确认密码"
      autocomplete="new-password"
      :maxlength="128"
      :error="passwordIssue?.field === 'confirmation' ? passwordIssue.message : undefined"
    />
    <button class="primary" type="submit" :disabled="submitting">
      {{ submitting ? '重置中…' : '重置密码' }}
    </button>
    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
  </form>
  <div class="auth-links">
    <RouterLink :to="{ name: 'forgot-password' }">重新发送验证码</RouterLink>
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
  </div>
</template>