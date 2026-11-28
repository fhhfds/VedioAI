import { defineStore } from 'pinia'
import { ref } from 'vue'

import {
  AuthApiError,
  fetchCsrf,
  fetchMe,
  loginAccount,
  logoutAccount,
  type AuthRequest,
  type AuthState,
  type Role,
} from '@/api/auth'

type AccountSession =
  | { status: 'unknown' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; account: AuthState }

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AccountSession>({ status: 'unknown' })
  const busy = ref(false)

  let bootstrap: Promise<void> | null = null
  const broadcastKey = 'vedioai.auth.changed'

  function broadcastIdentityChange() {
    try {
      window.localStorage.setItem(broadcastKey, `${Date.now()}-${Math.random()}`)
    } catch {
      // Storage may be unavailable in private or restricted contexts.
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      if (event.key !== broadcastKey) return
      void refresh().catch(() => {
        // Another tab changed identity but this tab could not refresh.
      })
    })
  }

  async function bootstrapSession() {
    try {
      // The CSRF endpoint issues the session cookie needed by the browser.
      await fetchCsrf()
      const account = await fetchMe()
      session.value = { status: 'authenticated', account }
    } catch (error) {
      session.value = { status: 'anonymous' }
      if (!(error instanceof AuthApiError && error.status === 401)) {
        // Network or server failures are treated as anonymous during startup;
        // the login form surfaces the real error when the user submits.
      }
    }
  }

  function initialize(): Promise<void> {
    if (session.value.status !== 'unknown') return Promise.resolve()
    bootstrap ??= bootstrapSession()
    return bootstrap
  }

  async function login(credentials: AuthRequest<'login'>): Promise<AuthState> {
    busy.value = true
    try {
      await loginAccount(credentials)
      // Login creates a PENDING guard; /me activates it before the UI treats
      // the session as authenticated.
      const account = await fetchMe()
      session.value = { status: 'authenticated', account }
      broadcastIdentityChange()
      return account
    } finally {
      busy.value = false
    }
  }

  async function refresh(): Promise<AuthState | null> {
    try {
      const account = await fetchMe()
      session.value = { status: 'authenticated', account }
      return account
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        session.value = { status: 'anonymous' }
        return null
      }
      throw error
    }
  }

  function applyUser(account: AuthState): void {
    session.value = { status: 'authenticated', account }
  }

  function markAnonymous(): void {
    session.value = { status: 'anonymous' }
    broadcastIdentityChange()
  }

  function hasRole(role: Role): boolean {
    return session.value.status === 'authenticated'
      && session.value.account.user.roles.includes(role)
  }

  async function logout(): Promise<void> {
    busy.value = true
    try {
      await logoutAccount()
    } catch (error) {
      if (error instanceof AuthApiError && error.status === 401) {
        session.value = { status: 'anonymous' }
        return
      }
      throw error
    } finally {
      busy.value = false
    }
    session.value = { status: 'anonymous' }
    broadcastIdentityChange()
  }

  return {
    session,
    busy,
    initialize,
    login,
    logout,
    refresh,
    applyUser,
    markAnonymous,
    hasRole,
  }
})