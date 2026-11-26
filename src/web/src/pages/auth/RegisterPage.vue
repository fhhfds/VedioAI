<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'

import type { AuthRequest } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import { passwordLengthHint, validateNewPassword } from '@/components/auth/formValidation'

const emailFields = reactive<AuthRequest<'registrationChallenge'>>({ email: '' })
const fields = reactive<Omit<AuthRequest<'register'>, 'challengeId'>>({ code: '', nickname: '', password: '' })
const confirmation = ref('')
const step = ref<'email' | 'details'>('email')
const emailInput = ref<HTMLInputElement>()
const codeInput = ref<HTMLInputElement>()
const passwordIssue = ref<ReturnType<typeof validateNewPassword>>(null)
const nicknameError = ref('')
const notice = ref('')

function clearFeedback() {
  nicknameError.value = ''
  passwordIssue.value = null
  notice.value = ''
}

async function switchStep(nextStep: 'email' | 'details') {
  clearFeedback()
  step.value = nextStep
  if (nextStep === 'email') {
    fields.code = ''
    fields.nickname = ''
    fields.password = ''
    confirmation.value = ''
  }
  await nextTick()
  const target = nextStep === 'email' ? emailInput.value : codeInput.value
  target?.focus()
}

function previewSendCode() {
  notice.value = '验证码服务尚未接入。你可以预览下一步表单。'
}

function previewRegistration() {
  nicknameError.value = fields.nickname ? '' : '请输入昵称。'
  passwordIssue.value = validateNewPassword(fields.password, confirmation.value)
  notice.value = nicknameError.value || passwordIssue.value ? '' : '输入格式已通过检查。注册服务尚未接入。'
}
</script>

<template>
  <h1 id="auth-heading">用邮箱注册</h1>
  <ol class="auth-steps" aria-label="注册步骤">
    <li :aria-current="step === 'email' ? 'step' : undefined">1. 验证邮箱</li>
    <li :aria-current="step === 'details' ? 'step' : undefined">2. 设置账号</li>
  </ol>

  <form
    v-if="step === 'email'"
    key="email"
    class="auth-form"
    @submit.prevent="previewSendCode"
    @input="clearFeedback"
  >
    <div class="auth-field">
      <label for="register-email">邮箱</label>
      <input
        id="register-email"
        ref="emailInput"
        v-model.trim="emailFields.email"
        type="email"
        autocomplete="username"
        maxlength="254"
        required
      >
    </div>
    <button class="primary" type="submit">发送验证码</button>
    <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
  </form>

  <form
    v-else
    key="details"
    class="auth-form"
    @submit.prevent="previewRegistration"
    @input="clearFeedback"
  >
    <div class="auth-field">
      <label for="register-code">邮箱验证码</label>
      <input
        id="register-code"
        ref="codeInput"
        v-model="fields.code"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]{8}"
        maxlength="8"
        aria-describedby="register-code-help"
        required
      >
      <p id="register-code-help" class="auth-hint">填写 8 位数字。静态预览可使用演示数字。</p>
    </div>
    <div class="auth-field">
      <label for="register-nickname">昵称</label>
      <input
        id="register-nickname"
        v-model.trim="fields.nickname"
        autocomplete="nickname"
        maxlength="64"
        :aria-invalid="nicknameError ? 'true' : undefined"
        :aria-describedby="nicknameError ? 'register-nickname-error' : undefined"
        required
      >
      <p v-if="nicknameError" id="register-nickname-error" class="auth-field-error" role="alert">{{ nicknameError }}</p>
    </div>
    <PasswordField
      v-model="fields.password"
      id="register-password"
      label="新密码"
      autocomplete="new-password"
      :hint="passwordLengthHint"
      :error="passwordIssue?.field === 'password' ? passwordIssue.message : undefined"
    />
    <PasswordField
      v-model="confirmation"
      id="register-confirmation"
      label="确认密码"
      autocomplete="new-password"
      :error="passwordIssue?.field === 'confirmation' ? passwordIssue.message : undefined"
    />
    <button class="primary" type="submit">创建账号</button>
    <p v-if="notice" class="auth-message" role="status">{{ notice }}</p>
  </form>

  <div class="auth-links">
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
    <button v-if="step === 'email'" class="auth-text-button" type="button" @click="switchStep('details')">
      预览设置账号
    </button>
    <button v-else class="auth-text-button" type="button" @click="switchStep('email')">返回验证邮箱</button>
  </div>
</template>
