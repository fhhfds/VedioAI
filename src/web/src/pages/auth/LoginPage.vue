<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { AuthApiError, type AuthRequest } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import { safeRedirect } from '@/router/redirect'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const fields = reactive<AuthRequest<'login'>>({ email: '', password: '' })
const notice = ref(
  route.query.registered === '1'
    ? '注册成功，请使用邮箱和密码登录。'
    : route.query.passwordChanged === '1'
      ? '密码已修改，请使用新密码重新登录。'
      : route.query.passwordReset === '1'
        ? '密码已重置，请使用新密码登录。'
        : '',
)
const errorMessage = ref('')
const submitting = ref(false)

function clearFeedback() {
  notice.value = ''
  errorMessage.value = ''
}

async function submitLogin() {
  if (submitting.value) return

  notice.value = ''
  errorMessage.value = ''

  if (!fields.email || !fields.password) {
    errorMessage.value = '请输入邮箱和密码。'
    return
  }

  submitting.value = true
  try {
    await auth.login({ email: fields.email, password: fields.password })
    await router.replace(safeRedirect(route.query.redirect) ?? { name: 'media' })
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '登录失败，请稍后重试。'
    fields.password = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <h1 id="auth-heading">登录 VedioAI</h1>
  <form class="auth-form" @submit.prevent="submitLogin" @input="clearFeedback">
    <div class="auth-field">
      <label for="login-email">邮箱</label>
      <input
        id="login-email"
        v-model.trim="fields.email"
        type="email"
        autocomplete="username"
        maxlength="254"
        required
      >
    </div>
    <PasswordField
      v-model="fields.password"
      id="login-password"
      label="密码"
      autocomplete="current-password"
      :maxlength="128"
    />
    <button class="primary" type="submit" :disabled="submitting">
      {{ submitting ? '登录中…' : '登录' }}
    </button>
    <p v-if="notice && !errorMessage" class="auth-message" role="status">{{ notice }}</p>
    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
  </form>
  <div class="auth-links">
    <RouterLink
      :to="{ name: 'register', query: safeRedirect(route.query.redirect) ? { redirect: route.query.redirect } : {} }"
    >用邮箱注册</RouterLink>
    <RouterLink :to="{ name: 'forgot-password' }">忘记密码</RouterLink>
  </div>
</template>