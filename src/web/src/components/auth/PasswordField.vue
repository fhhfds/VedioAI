<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  id: string
  label: string
  autocomplete: 'current-password' | 'new-password'
  hint?: string
  error?: string
  maxlength?: number
}>()

const password = defineModel<string>({ required: true })
const visible = ref(false)
</script>

<template>
  <div class="auth-field">
    <label :for="id">{{ label }}</label>
    <div class="auth-password">
      <input
        :id="id"
        v-model="password"
        :type="visible ? 'text' : 'password'"
        :autocomplete="props.autocomplete"
        :maxlength="maxlength"
        :aria-describedby="error || hint ? id + '-help' : undefined"
        :aria-invalid="error ? 'true' : undefined"
        required
      >
      <button
        type="button"
        class="auth-password-toggle"
        :aria-label="(visible ? '隐藏' : '显示') + label"
        @click="visible = !visible"
      >{{ visible ? '隐藏' : '显示' }}</button>
    </div>
    <p v-if="error || hint" :id="id + '-help'" :class="error ? 'auth-field-error' : 'auth-hint'" :role="error ? 'alert' : undefined">
      {{ error || hint }}
    </p>
  </div>
</template>
