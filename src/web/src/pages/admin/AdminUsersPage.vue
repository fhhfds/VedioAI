<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  AuthApiError,
  changeAdminUserRoles,
  changeAdminUserStatus,
  fetchAdminUsers,
  type AdminUser,
  type AdminUserPage,
  type UserStatus,
} from '@/api/auth'
import ReauthDialog from '@/components/auth/ReauthDialog.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const page = ref(1)
const pageSize = 20
const statusFilter = ref<'' | UserStatus>('')
const data = ref<AdminUserPage>({ items: [], page: 1, pageSize, total: 0 })
const loading = ref(false)
const errorMessage = ref('')
const reauthOpen = ref(false)
const pendingAction = ref<
  | { type: 'status'; user: AdminUser; nextStatus: UserStatus }
  | { type: 'roles'; user: AdminUser; nextRoles: ['USER'] | ['USER', 'ADMIN'] }
  | null
>(null)

const currentUserId = computed(() =>
  auth.session.status === 'authenticated' ? auth.session.account.user.userId : '',
)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / pageSize)))

onMounted(load)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    data.value = await fetchAdminUsers(
      page.value,
      pageSize,
      statusFilter.value || undefined,
    )
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '无法加载账号列表。'
  } finally {
    loading.value = false
  }
}

function changeFilter() {
  page.value = 1
  void load()
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || loading.value) return
  page.value = next
  void load()
}

function requestStatusChange(user: AdminUser) {
  if (user.userId === currentUserId.value) {
    errorMessage.value = '不能通过管理接口修改当前登录账号。'
    return
  }
  pendingAction.value = {
    type: 'status',
    user,
    nextStatus: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
  }
  reauthOpen.value = true
}

function requestRoleChange(user: AdminUser) {
  if (user.userId === currentUserId.value) {
    errorMessage.value = '不能通过管理接口修改当前登录账号。'
    return
  }
  pendingAction.value = {
    type: 'roles',
    user,
    nextRoles: user.roles.includes('ADMIN') ? ['USER'] : ['USER', 'ADMIN'],
  }
  reauthOpen.value = true
}

async function runPendingAction() {
  const action = pendingAction.value
  if (!action) return

  errorMessage.value = ''
  try {
    const updated = action.type === 'status'
      ? await changeAdminUserStatus(action.user.userId, {
          status: action.nextStatus,
          expectedVersion: action.user.version,
        })
      : await changeAdminUserRoles(action.user.userId, {
          roles: action.nextRoles,
          expectedVersion: action.user.version,
        })

    const index = data.value.items.findIndex((item) => item.userId === updated.userId)
    if (index >= 0) data.value.items[index] = updated
  } catch (error) {
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '管理操作失败，请稍后重试。'
  } finally {
    pendingAction.value = null
  }
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <section class="page" aria-labelledby="admin-users-heading">
    <header class="page-header admin-header">
      <div>
        <p class="eyebrow">ADMIN</p>
        <h1 id="admin-users-heading">账号管理</h1>
        <p class="lead">停用账号或调整固定角色会提升目标身份版本并撤销其全部会话。</p>
      </div>
      <label class="admin-filter">
        状态
        <select v-model="statusFilter" @change="changeFilter">
          <option value="">全部</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="DISABLED">DISABLED</option>
        </select>
      </label>
    </header>

    <p v-if="errorMessage" class="auth-message auth-field-error" role="alert">{{ errorMessage }}</p>

    <div class="card admin-table-card">
      <p v-if="loading" class="auth-hint">正在加载账号…</p>
      <p v-else-if="data.items.length === 0" class="auth-hint">没有符合条件的账号。</p>

      <ul v-else class="admin-user-list">
        <li v-for="user in data.items" :key="user.userId" class="admin-user">
          <div class="admin-user-main">
            <div class="admin-user-title">
              <strong>{{ user.nickname }}</strong>
              <span :class="['admin-status', user.status.toLowerCase()]">{{ user.status }}</span>
              <span v-for="role in user.roles" :key="role" class="admin-role">{{ role }}</span>
              <span v-if="user.userId === currentUserId" class="admin-role self">当前账号</span>
            </div>
            <p>{{ user.emailLabel }} · {{ user.userId }}</p>
            <p class="auth-hint">版本 {{ user.version }} · 创建于 {{ formatTime(user.createdAt) }}</p>
          </div>

          <div class="admin-actions">
            <button
              type="button"
              :disabled="user.userId === currentUserId"
              @click="requestStatusChange(user)"
            >
              {{ user.status === 'ACTIVE' ? '停用' : '启用' }}
            </button>
            <button
              type="button"
              :disabled="user.userId === currentUserId"
              @click="requestRoleChange(user)"
            >
              {{ user.roles.includes('ADMIN') ? '移除管理员' : '设为管理员' }}
            </button>
          </div>
        </li>
      </ul>

      <nav class="admin-pagination" aria-label="分页">
        <button type="button" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页，共 {{ data.total }} 个账号</span>
        <button type="button" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">下一页</button>
      </nav>
    </div>

    <p class="auth-hint">
      管理写操作需要重新输入当前密码。后端同时校验 ADMIN 角色、目标版本、自改和最后管理员保护。
    </p>
  </section>

  <ReauthDialog
    :open="reauthOpen"
    title="确认管理操作"
    description="管理操作会撤销目标账号的全部会话，请先验证当前密码。"
    @close="reauthOpen = false"
    @success="runPendingAction"
  />
</template>

<style scoped>
.admin-header {
  display: flex;
  max-width: none;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.admin-filter {
  display: grid;
  gap: 6px;
  color: var(--muted);
  font-size: 13px;
}

.admin-filter select {
  min-width: 130px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  color: var(--text);
  font: inherit;
}

.admin-table-card {
  padding: 0;
  overflow: hidden;
}

.admin-user-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.admin-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}

.admin-user:last-child {
  border-bottom: 0;
}

.admin-user-main {
  min-width: 0;
}

.admin-user-main p {
  margin: 5px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.admin-user-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.admin-status,
.admin-role {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
}

.admin-status {
  background: var(--accent-soft);
  color: var(--accent);
}

.admin-status.disabled {
  background: #fff1ef;
  color: var(--error);
}

.admin-role {
  background: var(--bg);
  color: var(--muted);
}

.admin-role.self {
  background: #eaf7ef;
  color: var(--ok);
}

.admin-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.admin-actions button,
.admin-pagination button {
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}

.admin-actions button:hover:not(:disabled),
.admin-pagination button:hover:not(:disabled) {
  background: var(--accent-soft);
}

.admin-actions button:disabled,
.admin-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.admin-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 14px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 640px) {
  .admin-header,
  .admin-user {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-actions button {
    flex: 1;
  }

  .admin-pagination {
    flex-wrap: wrap;
  }
}
</style>