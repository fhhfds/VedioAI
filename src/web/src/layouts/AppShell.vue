<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { AuthApiError } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const links = [
  { to: 'media', label: '素材库' },
  { to: 'narration', label: 'AI 解说' },
  { to: 'highlights', label: '高光打点' },
  { to: 'artifacts', label: '成果库' },
] as const

type Panel = 'tasks' | 'account'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activePanel = ref<Panel | null>(null)
const actions = ref<HTMLElement>()
const taskButton = ref<HTMLButtonElement>()
const accountButton = ref<HTMLButtonElement>()
const logoutError = ref('')
const loggingOut = ref(false)

const account = computed(() =>
  auth.session.status === 'authenticated' ? auth.session.account.user : null,
)
const accountInitial = computed(() => account.value?.nickname.trim().charAt(0).toUpperCase() ?? '')

function togglePanel(panel: Panel) {
  logoutError.value = ''
  activePanel.value = activePanel.value === panel ? null : panel
}

async function signOut() {
  if (loggingOut.value) return

  logoutError.value = ''
  loggingOut.value = true
  try {
    await auth.logout()
    activePanel.value = null
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = error instanceof AuthApiError
      ? error.message
      : '退出登录失败，请稍后重试。'
  } finally {
    loggingOut.value = false
  }
}

async function closeOnEscape(event: KeyboardEvent) {
  if (!activePanel.value) return
  event.preventDefault()
  const trigger = activePanel.value === 'tasks' ? taskButton.value : accountButton.value
  activePanel.value = null
  await nextTick()
  trigger?.focus()
}

function closeOnOutsideClick(event: PointerEvent) {
  if (event.target instanceof Node && !actions.value?.contains(event.target)) {
    activePanel.value = null
  }
}

watch(() => route.fullPath, () => { activePanel.value = null })

watch(() => auth.session.status, async (status) => {
  if (status !== 'anonymous') return
  activePanel.value = null
  if (route.meta.requiresAuth) {
    await router.replace({ name: 'login' })
  }
})

onMounted(() => document.addEventListener('pointerdown', closeOnOutsideClick))
onUnmounted(() => document.removeEventListener('pointerdown', closeOnOutsideClick))
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <RouterLink class="brand" :to="{ name: 'media' }">
        <span class="brand-mark" aria-hidden="true"></span>
        VedioAI
      </RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="{ name: link.to }"
          class="nav-link"
        >{{ link.label }}</RouterLink>
      </nav>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <span class="location">{{ route.meta.title }}</span>

        <div ref="actions" class="topbar-actions" @keydown.esc="closeOnEscape">
          <div class="topbar-control">
            <button
              ref="taskButton"
              class="topbar-button"
              type="button"
              aria-label="任务"
              :aria-expanded="activePanel === 'tasks'"
              aria-controls="task-panel"
              @click="togglePanel('tasks')"
            >
              <svg
                viewBox="0 0 24 24"
                width="18"
                height="18"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
                <rect x="9" y="3" width="6" height="4" rx="1" />
                <path d="M9 12h6M9 16h4" />
              </svg>
            </button>
            <section v-show="activePanel === 'tasks'" id="task-panel" class="popover task-panel" aria-label="任务">
              <h2>任务</h2>
              <p>任务服务尚未接入。</p>
            </section>
          </div>

          <div class="topbar-control">
            <button
              ref="accountButton"
              class="topbar-button account-button"
              type="button"
              aria-label="账户"
              :aria-expanded="activePanel === 'account'"
              aria-controls="account-panel"
              @click="togglePanel('account')"
            >
              <span v-if="accountInitial" class="account-initial" aria-hidden="true">{{ accountInitial }}</span>
              <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                <circle cx="12" cy="8" r="3.2" />
                <path d="M5.5 19a6.5 6.5 0 0 1 13 0" stroke-linecap="round" />
              </svg>
            </button>
            <nav v-show="activePanel === 'account'" id="account-panel" class="popover account-panel" aria-label="账户">
              <template v-if="account">
                <div class="account-identity">
                  <strong>{{ account.nickname }}</strong>
                  <span>{{ account.email }}</span>
                </div>
                <RouterLink :to="{ name: 'account-security' }" @click="activePanel = null">账户安全</RouterLink>
                <RouterLink
                  v-if="auth.hasRole('ADMIN')"
                  :to="{ name: 'admin-users' }"
                  @click="activePanel = null"
                >账号管理</RouterLink>
                <button class="account-signout" type="button" :disabled="loggingOut" @click="signOut">
                  {{ loggingOut ? '退出中…' : '退出登录' }}
                </button>
                <p v-if="logoutError" class="account-error" role="alert">{{ logoutError }}</p>
              </template>
              <template v-else>
                <RouterLink :to="{ name: 'login' }" @click="activePanel = null">登录</RouterLink>
                <RouterLink :to="{ name: 'register' }" @click="activePanel = null">注册</RouterLink>
              </template>
            </nav>
          </div>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  height: 100dvh;
  flex-direction: column;
  padding: 23px 14px;
  background: var(--stage);
  color: #f7f5ff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 9px 36px;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.035em;
}

.brand-mark {
  width: 23px;
  height: 23px;
  flex: 0 0 23px;
  border: 2px solid #ad9dff;
  border-radius: 7px;
  background: radial-gradient(circle, var(--time-marker) 0 2px, #715bf0 3px 6px, transparent 7px);
}

.nav {
  display: grid;
  gap: 5px;
}

.nav-link {
  position: relative;
  padding: 11px 13px;
  border-radius: 8px;
  color: #d6d0ec;
  font-size: 14px;
  font-weight: 600;
}

.nav-link:hover,
.nav-link.router-link-active {
  background: #2d274a;
  color: #fff;
}

.nav-link.router-link-active::before {
  position: absolute;
  top: 9px;
  bottom: 9px;
  left: 0;
  width: 3px;
  border-radius: 2px;
  background: var(--time-marker);
  content: '';
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  min-height: 60px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
  background: var(--panel);
}

.location {
  font-size: 14px;
  font-weight: 650;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-control {
  position: relative;
}

.topbar-button {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}

.topbar-button:hover,
.topbar-button[aria-expanded='true'] {
  background: var(--accent-soft);
  color: var(--accent);
}

.account-button {
  border-radius: 50%;
}

.account-initial {
  font-size: 15px;
  font-weight: 750;
  line-height: 1;
}

.popover {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 182px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
  box-shadow: 0 12px 32px rgb(32 24 65 / 14%);
}

.task-panel {
  width: min(440px, calc(100vw - 80px));
  max-height: min(580px, calc(100dvh - 84px));
  overflow: auto;
  padding: 15px;
}

.task-panel h2 {
  margin: 0 0 7px;
  font-size: 16px;
}

.task-panel p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.account-panel a {
  display: block;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 14px;
}

.account-panel a:hover {
  background: var(--accent-soft);
}

.account-identity {
  display: grid;
  gap: 2px;
  margin-bottom: 6px;
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--line);
}

.account-identity strong {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-identity span {
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-signout {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--error);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  text-align: left;
}

.account-signout:hover {
  background: var(--accent-soft);
}

.account-signout:disabled {
  cursor: progress;
  opacity: 0.65;
}

.account-error {
  margin: 4px 6px 0;
  color: var(--error);
  font-size: 12px;
  line-height: 1.5;
}

.content {
  width: min(1120px, 100%);
  margin: 0 auto;
  padding: 28px 24px 48px;
}

@media (761px <= width <= 1180px) {
  .shell {
    grid-template-columns: 84px minmax(0, 1fr);
  }

  .sidebar {
    padding-inline: 6px;
  }

  .brand {
    justify-content: center;
    margin-inline: 0;
    font-size: 0;
  }

  .nav-link {
    padding-inline: 5px;
    text-align: center;
    font-size: 12px;
  }
}

@media (max-width: 760px) {
  .shell {
    display: block;
  }

  .sidebar {
    position: fixed;
    inset: auto 0 0;
    z-index: 11;
    height: auto;
    padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
  }

  .brand {
    display: none;
  }

  .nav {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .nav-link {
    padding-inline: 3px;
    text-align: center;
    font-size: 12px;
  }

  .topbar {
    padding-inline: 16px;
  }

  .content {
    padding: 20px 16px 82px;
  }
}
</style>
