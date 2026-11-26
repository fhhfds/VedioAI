<script setup lang="ts">
import { reactive, ref } from 'vue'

import type { AuthRequest } from '@/api/auth'

const fields = reactive<AuthRequest<'resetChallenge'>>({ email: '' })
const notice = ref('')

function previewResetRequest() {
  notice.value = '验证码服务尚未接入。你可以预览重置密码表单。'
}
</script>

<template>
  <h1 id="auth-heading">找回密码</h1>
  <p class="lead">填写登录邮箱，使用邮箱验证码重置密码。</p>
  <form class="auth-form" @submit.prevent="previewResetRequest" @input="notice = ''">
    <div class="auth-field">
      <label for="recovery-email">邮箱</label>
      <input id="recovery-email" v-model.trim="fields.email" type="email" autocomplete="username" maxlength="254" required>
    </div>
    <button class="primary" type="submit">发送重置验证码</button>
    <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
  </form>
  <div class="auth-links">
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
    <RouterLink :to="{ name: 'reset-password' }">预览重置密码</RouterLink>
  </div>
</template>
