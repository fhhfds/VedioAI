<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  AuthApiError,
  changePassword,
  fetchSessions,
  revokeAllSessions,
  revokeSession,
  updateProfile,
  type SessionItem,
} from '@/api/auth'
import PasswordField from '@/components/auth/PasswordField.vue'
import ReauthDialog from '@/components/auth/ReauthDialog.vue'
import { passwordLengthHint, validateNewPassword } from '@/components/auth/formValidation'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const user = computed(() =>
  auth.session.status === 'authenticated' ? auth.session.account.user : null,
)
const currentSession = computed(() =>
  auth.session.status === 'authenticated' ? auth.session.account.session : null,
)

const profile = reactive({ nickname: '' })
const profileNotice = ref('')
const profileError = ref('')
const savingProfile = ref(false)

const passwordForm = reactive({ currentPassword: '', newPassword: '' })
const passwordConfirmation = ref('')
const passwordIssue = ref<ReturnType<typeof validateNewPassword>>(null)
const passwordNotice = ref('')
const passwordError = ref('')
const changingPassword = ref(false)

const sessions = ref<SessionItem[]>([])
const sessionsLoading = ref(false)
const sessionsError = ref('')
const reauthOpen = ref(false)

watch(() => user.value?.nickname, (nickname) => {
  if (nickname) profile.nickname = nickname
}, { immediate: true })

onMounted(loadSessions)

async function loadSessions() {
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    sessions.value = (await fetchSessions()).items
  } catch (error) {
    sessionsError.value = error instanceof AuthApiError
      ? error.message
      : '无法加载登录设备。'
  } finally {
    sessionsLoading.value = false
  }
}

async function saveProfile() {
  if (savingProfile.value || !user.value) return

  profileNotice.value = ''
  profileError.value = ''
  if (!profile.nickname.trim()) {
    profileError.value = '请输入昵称。'
    return
  }

  savingProfile.value = true
  try {
    const updated = await updateProfile({
      nickname: profile.nickname,
      expectedVersion: user.value.version,
    })
    if (auth.session.status === 'authenticated') {
      auth.applyUser({ ...auth.session.account, user: updated })
    }
    profile.nickname = updated.nickname
    profileNotice.value = '昵称已更新。'
  } catch (error) {
    profileError.value = error instanceof AuthApiError
      ? error.message
      : '更新失败，请稍后重试。'
  } finally {
    savingProfile.value = false
  }
}

async function submitPasswordChange() {
  if (changingPassword.value) return

  passwordNotice.value = ''
  passwordError.value = ''
  passwordIssue.value = validateNewPassword(
    passwordForm.newPassword,
    passwordConfirmation.value,
  )
  if (passwordIssue.value) return

  changingPassword.value = true
  try {
    await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    auth.markAnonymous()
    await router.replace({ name: 'login', query: { passwordChanged: '1' } })
  } catch (error) {
    passwordError.value = error instanceof AuthApiError
      ? error.message
      : '修改密码失败，请稍后重试。'
  } finally {
    changingPassword.value = false
  }
}

async function revokeOne(item: SessionItem) {
  if (sessionsLoading.value) return
  if (item.current && !window.confirm('撤销当前设备会让你立即退出登录，继续吗？')) return

  sessionsError.value = ''
  try {
    await revokeSession(item.sessionId)
    if (item.current) {
      auth.markAnonymous()
      await router.replace({ name: 'login' })
      return
    }
    await loadSessions()
  } catch (error) {
    sessionsError.value = error instanceof AuthApiError
      ? error.message
      : '撤销会话失败。'
  }
}

async function revokeEverywhere() {
  sessionsError.value = ''
  try {
    await revokeAllSessions()
    auth.markAnonymous()
    reauthOpen.value = false
    await router.replace({ name: 'login' })
  } catch (error) {
    sessionsError.value = error instanceof AuthApiError
      ? error.message
      : '退出全部设备失败。'
  }
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <section class="page" aria-labelledby="account-heading">
    <header class="page-header">
      <h1 id="account-heading">账户安全</h1>
      <p v-if="user" class="lead">
        {{ user.nickname }} · {{ user.email }}
      </p>
      <p v-else class="lead">正在确认当前会话。</p>
    </header>

    <div class="account-grid">
      <section class="card">
        <h2>个人资料</h2>
        <p v-if="user">只修改显示昵称；邮箱、角色和状态不能在这里修改。</p>
        <form class="account-form" @submit.prevent="saveProfile" @input="profileNotice = ''; profileError = ''">
          <div class="auth-field">
            <label for="profile-nickname">昵称</label>
            <input
              id="profile-nickname"
              v-model.trim="profile.nickname"
              maxlength="64"
              autocomplete="nickname"
              required
            >
            <p class="auth-hint">当前版本 {{ user?.version ?? '—' }}；多人同时修改时后提交者会收到冲突提示。</p>
          </div>
          <button class="primary" type="submit" :disabled="savingProfile || !user">
            {{ savingProfile ? '保存中…' : '保存昵称' }}
          </button>
          <p v-if="profileNotice" class="auth-message" role="status">{{ profileNotice }}</p>
          <p v-if="profileError" class="auth-message auth-field-error" role="alert">{{ profileError }}</p>
        </form>
      </section>

      <section class="card">
        <h2>修改密码</h2>
        <p>修改成功后，所有设备（包括当前设备）都需要重新登录。</p>
        <form class="account-form" @submit.prevent="submitPasswordChange" @input="passwordNotice = ''; passwordError = ''; passwordIssue = null">
          <PasswordField
            v-model="passwordForm.currentPassword"
            id="current-password"
            label="当前密码"
            autocomplete="current-password"
            :maxlength="1024"
          />
          <PasswordField
            v-model="passwordForm.newPassword"
            id="new-password"
            label="新密码"
            autocomplete="new-password"
            :maxlength="128"
            :hint="passwordLengthHint"
            :error="passwordIssue?.field === 'password' ? passwordIssue.message : undefined"
          />
          <PasswordField
            v-model="passwordConfirmation"
            id="confirm-password"
            label="确认新密码"
            autocomplete="new-password"
            :maxlength="128"
            :error="passwordIssue?.field === 'confirmation' ? passwordIssue.message : undefined"
          />
          <button class="primary" type="submit" :disabled="changingPassword">
            {{ changingPassword ? '修改中…' : '修改密码' }}
          </button>
          <p v-if="passwordNotice" class="auth-message" role="status">{{ passwordNotice }}</p>
          <p v-if="passwordError" class="auth-message auth-field-error" role="alert">{{ passwordError }}</p>
        </form>
      </section>
    </div>

    <section class="card">
      <div class="sessions-header">
        <div>
          <h2>登录设备</h2>
          <p>只显示你自己的公开会话 ID，不会暴露框架 Session ID 或 Cookie。</p>
        </div>
        <button class="danger-button" type="button" @click="reauthOpen = true">退出全部设备</button>
      </div>

      <p v-if="sessionsLoading" class="auth-hint">正在加载登录设备…</p>
      <p v-else-if="sessionsError" class="auth-message auth-field-error" role="alert">{{ sessionsError }}</p>
      <p v-else-if="sessions.length === 0" class="auth-hint">没有可显示的活动会话。</p>

      <ul v-else class="session-list">
        <li v-for="item in sessions" :key="item.sessionId" class="session-item">
          <div class="session-main">
            <div>
              <strong>{{ item.userAgent }}</strong>
              <span v-if="item.current" class="session-current">当前设备</span>
            </div>
            <p>{{ item.sessionId }}</p>
            <p class="auth-hint">
              最近活动 {{ formatTime(item.lastSeenAt) }} · 绝对到期 {{ formatTime(item.expiresAt) }}
            </p>
          </div>
          <button type="button" class="session-revoke" @click="revokeOne(item)">
            {{ item.current ? '退出此设备' : '撤销' }}
          </button>
        </li>
      </ul>

      <p class="auth-hint">
        当前会话：{{ currentSession?.sessionId }}。退出全部设备需要重新输入当前密码。
      </p>
      <p v-if="user" class="auth-hint">
        权限：{{ user.permissions.length }} 项，角色 {{ user.roles.join(' / ') }}。
      </p>
    </section>
  </section>

  <ReauthDialog
    :open="reauthOpen"
    title="退出全部设备"
    description="这会提升身份版本并撤销所有设备，包括当前设备。请先验证当前密码。"
    @close="reauthOpen = false"
    @success="revokeEverywhere"
  />
</template>

<style scoped>
.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.account-form {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.account-form > .primary {
  width: fit-content;
}

.sessions-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.sessions-header h2 {
  margin-bottom: 6px;
}

.danger-button {
  flex: 0 0 auto;
  padding: 9px 14px;
  border: 1px solid #e0b4ae;
  border-radius: 9px;
  background: #fff5f3;
  color: var(--error);
  cursor: pointer;
  font-weight: 600;
}

.session-list {
  display: grid;
  gap: 10px;
  margin: 16px 0;
  padding: 0;
  list-style: none;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg);
}

.session-main {
  min-width: 0;
}

.session-main p {
  margin: 4px 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.session-main strong {
  margin-right: 8px;
}

.session-current {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 650;
}

.session-revoke {
  flex: 0 0 auto;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  color: var(--error);
  cursor: pointer;
}

@media (max-width: 620px) {
  .sessions-header,
  .session-item {
    align-items: stretch;
    flex-direction: column;
  }

  .danger-button,
  .session-revoke {
    width: 100%;
  }
}
</style>