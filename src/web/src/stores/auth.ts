import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { AuthState } from '@/api/auth'

type AccountSession =
  | { status: 'unknown' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; account: AuthState }

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AccountSession>({ status: 'unknown' })

  function initializePreview() {
    session.value = { status: 'anonymous' }
  }

  return { session, initializePreview }
})
