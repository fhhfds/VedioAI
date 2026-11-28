<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

import { AuthApiError, reauthenticate } from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'

const props = defineProps<{
  open: boolean
  title?: string
  description?: string
}>()

const emit = defineEmits<{
  close: []
  success: [{ validUntil: string }]
}>()

const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)
const input = ref<HTMLInputElement>()

watch(() => props.open, async (open) => {
  if (!open) return
  password.value = ''
  errorMessage.value = ''
  await nextTick()
  document.getElementById('reauth-password')?.focus()
})

async function submit() {
  if (submitting.value) return
  if (!password.value) {
    errorMessage.value = '请输入当前密码。'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await reauthenticate(password.value)
    password.value = ''
    emit('success', result)
    emit('close')
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '验证失败，请稍后重试。'
    password.value = ''
    input.value?.focus()
  } finally {
    submitting.value = false
  }
}

function close() {
  if (!submitting.value) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="reauth-backdrop" @click.self="close" @keydown.esc="close">
      <section
        class="reauth-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="reauth-title"
        tabindex="-1"
      >
        <h2 id="reauth-title">{{ title || '确认当前密码' }}</h2>
        <p class="reauth-description">
          {{ description || '该操作会撤销其他登录会话，请先验证当前密码。' }}
        </p>
        <form class="auth-form" @submit.prevent="submit">
          <PasswordField
            v-model="password"
            id="reauth-password"
            label="当前密码"
            autocomplete="current-password"
            :maxlength="1024"
          />
          <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>
          <div class="reauth-actions">
            <button class="reauth-secondary" type="button" :disabled="submitting" @click="close">取消</button>
            <button class="primary" type="submit" :disabled="submitting">
              {{ submitting ? '验证中…' : '确认' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.reauth-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgb(20 16 40 / 46%);
}

.reauth-dialog {
  width: min(100%, 430px);
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--panel);
  box-shadow: 0 24px 64px rgb(20 16 40 / 28%);
}

.reauth-dialog h2 {
  margin: 0 0 8px;
  font-size: 21px;
}

.reauth-description {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.65;
}

.reauth-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.reauth-secondary {
  padding: 9px 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}
</style>