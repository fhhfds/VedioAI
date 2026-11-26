<script setup lang="ts">
import { reactive, ref } from 'vue'

import type { AuthRequest } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'

const fields = reactive<AuthRequest<'login'>>({ email: '', password: '' })
const notice = ref('')

function previewLogin() {
  notice.value = '输入格式已通过检查。登录服务尚未接入。'
}
</script>

<template>
  <h1 id="auth-heading">登录 VedioAI</h1>
  <form class="auth-form" @submit.prevent="previewLogin" @input="notice = ''">
    <div class="auth-field">
      <label for="login-email">邮箱</label>
      <input id="login-email" v-model.trim="fields.email" type="email" autocomplete="username" maxlength="254" required>
    </div>
    <PasswordField v-model="fields.password" id="login-password" label="密码" autocomplete="current-password" />
    <button class="primary" type="submit">登录</button>
    <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
  </form>
  <div class="auth-links">
    <RouterLink :to="{ name: 'register' }">用邮箱注册</RouterLink>
    <RouterLink :to="{ name: 'forgot-password' }">忘记密码</RouterLink>
  </div>
</template>
