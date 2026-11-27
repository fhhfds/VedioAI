<script setup lang="ts">
import { nextTick, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  AuthApiError,
  registerAccount,
  requestRegistrationChallenge,
  type AuthRequest,
} from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import { passwordLengthHint, validateNewPassword } from '@/components/auth/formValidation'

const router = useRouter()

const emailFields = reactive<AuthRequest<'registrationChallenge'>>({ email: '' })
const fields = reactive<Omit<AuthRequest<'register'>, 'challengeId'>>({
  code: '',
  nickname: '',
  password: '',
})
const confirmation = ref('')
const challengeId = ref('')
const challengeEmail = ref('')
const step = ref<'email' | 'details'>('email')
const emailInput = ref<HTMLInputElement>()
const codeInput = ref<HTMLInputElement>()
const passwordIssue = ref<ReturnType<typeof validateNewPassword>>(null)
const nicknameError = ref('')
const notice = ref('')
const errorMessage = ref('')
const sending = ref(false)
const submitting = ref(false)
const completed = ref(false)
const resendIn = ref(0)
let resendTimer: number | undefined

function stopCountdown() {
  if (resendTimer !== undefined) {
    window.clearInterval(resendTimer)
    resendTimer = undefined
  }
}

function startCountdown(seconds: number) {
  stopCountdown()
  resendIn.value = Math.max(0, Math.floor(seconds))
  resendTimer = window.setInterval(() => {
    if (resendIn.value > 0) {
      resendIn.value -= 1
    } else {
      stopCountdown()
    }
  }, 1000)
}

onUnmounted(stopCountdown)

function clearFeedback() {
  nicknameError.value = ''
  passwordIssue.value = null
  notice.value = ''
  errorMessage.value = ''
}

function showError(error: unknown) {
  errorMessage.value = error instanceof AuthApiError
    ? error.message
    : '操作失败，请稍后重试。'
}

async function focusDetails() {
  await nextTick()
  codeInput.value?.focus()
}

async function sendCode() {
  if (sending.value || completed.value) return

  clearFeedback()
  if (!emailFields.email) {
    errorMessage.value = '请输入邮箱。'
    return
  }

  sending.value = true
  try {
    const receipt = await requestRegistrationChallenge(emailFields.email)
    challengeId.value = receipt.challengeId
    challengeEmail.value = emailFields.email
    startCountdown(receipt.resendAfter)
    step.value = 'details'
    await focusDetails()
    notice.value = '如果该邮箱可用，验证码已发送。请在本机 Mailpit 收件箱查看。'
  } catch (error) {
    showError(error)
  } finally {
    sending.value = false
  }
}

async function resendCode() {
  if (sending.value || resendIn.value > 0 || completed.value) return
  if (!emailFields.email) {
    step.value = 'email'
    return
  }

  clearFeedback()
  sending.value = true
  try {
    const receipt = await requestRegistrationChallenge(emailFields.email)
    challengeId.value = receipt.challengeId
    challengeEmail.value = emailFields.email
    startCountdown(receipt.resendAfter)
    notice.value = '如果该邮箱可用，新的验证码已发送。'
  } catch (error) {
    showError(error)
  } finally {
    sending.value = false
  }
}

async function switchStep(nextStep: 'email' | 'details') {
  if (submitting.value) return

  clearFeedback()
  step.value = nextStep

  if (nextStep === 'email') {
    challengeId.value = ''
    challengeEmail.value = ''
    fields.code = ''
    fields.nickname = ''
    fields.password = ''
    confirmation.value = ''
    stopCountdown()
  }

  await nextTick()
  const target = nextStep === 'email' ? emailInput.value : codeInput.value
  target?.focus()
}

async function submitRegistration() {
  if (submitting.value || completed.value) return

  clearFeedback()
  nicknameError.value = fields.nickname.trim() ? '' : '请输入昵称。'
  passwordIssue.value = validateNewPassword(fields.password, confirmation.value)
  if (nicknameError.value || passwordIssue.value) return

  if (!challengeId.value) {
    errorMessage.value = '请先获取验证码。'
    step.value = 'email'
    return
  }

  submitting.value = true
  try {
    await registerAccount({
      challengeId: challengeId.value,
      code: fields.code,
      nickname: fields.nickname,
      password: fields.password,
    })
    completed.value = true
    fields.password = ''
    confirmation.value = ''
    notice.value = '注册成功，请登录。'
    window.setTimeout(() => {
      void router.push({ name: 'login' })
    }, 1200)
  } catch (error) {
    showError(error)
  } finally {
    submitting.value = false
  }
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
    @submit.prevent="sendCode"
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
    <button class="primary" type="submit" :disabled="sending || completed">
      {{ sending ? '发送中…' : '发送验证码' }}
    </button>
    <p v-if="notice && !errorMessage" class="auth-message" role="status">{{ notice }}</p>
    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
  </form>

  <form
    v-else
    key="details"
    class="auth-form"
    @submit.prevent="submitRegistration"
    @input="clearFeedback"
  >
    <p class="auth-hint">
      验证码已发送至 {{ challengeEmail }}。若未收到，请在本机 Mailpit 收件箱查看。
    </p>
    <div class="auth-field">
      <label for="register-code">邮箱验证码</label>
      <input
        id="register-code"
        ref="codeInput"
        v-model="fields.code"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]{6}"
        maxlength="6"
        aria-describedby="register-code-help"
        required
      >
      <p id="register-code-help" class="auth-hint">填写邮件中的 6 位数字。</p>
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
      :maxlength="128"
      :hint="passwordLengthHint"
      :error="passwordIssue?.field === 'password' ? passwordIssue.message : undefined"
    />
    <PasswordField
      v-model="confirmation"
      id="register-confirmation"
      label="确认密码"
      autocomplete="new-password"
      :maxlength="128"
      :error="passwordIssue?.field === 'confirmation' ? passwordIssue.message : undefined"
    />
    <button class="primary" type="submit" :disabled="submitting || completed">
      {{ submitting ? '创建中…' : '创建账号' }}
    </button>
    <p v-if="notice && !errorMessage" class="auth-message" role="status">{{ notice }}</p>
    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
  </form>

  <div class="auth-links">
    <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
    <template v-if="step === 'details'">
      <button
        class="auth-text-button"
        type="button"
        :disabled="sending || resendIn > 0 || completed"
        @click="resendCode"
      >
        {{ resendIn > 0 ? `${resendIn} 秒后可重发` : '重新发送验证码' }}
      </button>
      <button class="auth-text-button" type="button" :disabled="submitting" @click="switchStep('email')">
        修改邮箱
      </button>
    </template>
  </div>
</template>