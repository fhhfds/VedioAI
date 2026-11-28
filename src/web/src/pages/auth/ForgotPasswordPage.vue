<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { AuthApiError, requestPasswordResetChallenge, type AuthRequest } from '@/api/auth'

const router = useRouter()
const fields = reactive<AuthRequest<'resetChallenge'>>({ email: '' })
const notice = ref('')
const errorMessage = ref('')
const sending = ref(false)

function clearFeedback() {
  notice.value = ''
  errorMessage.value = ''
}

async function requestReset() {
  if (sending.value) return
  clearFeedback()
  if (!fields.email) {
    errorMessage.value = '请输入邮箱。'
    return
  }

  sending.value = true
  try {
    const receipt = await requestPasswordResetChallenge(fields.email)
    notice.value = '如果该邮箱可用，重置验证码已发送。请检查收件箱（包括垃圾邮件）。'
    await router.push({
      name: 'reset-password',
      query: { challengeId: receipt.challengeId },
    })
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '发送失败，请稍后重试。'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <h1 id="auth-heading">找回密码</h1>
  <p class="lead">填写登录邮箱，使用邮箱验证码重置密码。</p>
  <form class="auth-form" @submit.prevent="requestReset" @input="clearFeedback">
    <div class="auth-field">
      <label for="recovery-email">邮箱</label>
      <input
        id="recovery-email"
        v-model.trim="fields.email"
        type="email"
        autocomplete="username"
        maxlength="254"
        required
      >
    </div>
    <button class="primary" type="submit" :disabled="sending">
      {{ sending ? '发送中…' : '发送重置验证码' }}
    </button>
    <p v-if="notice && !errorMessage" class="auth-message" role="status">{{ notice }}</p>
    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
  </form>
  <div class="auth-links">
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
    <RouterLink :to="{ name: 'reset-password' }">已有验证码</RouterLink>
  </div>
</template>