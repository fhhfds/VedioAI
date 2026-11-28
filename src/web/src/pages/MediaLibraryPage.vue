<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { AuthApiError } from '@/api/auth'
import {
  fetchMediaDetail,
  fetchMediaList,
  fetchMediaPolicy,
  type MediaDetail,
  type MediaPolicy,
  type MediaSort,
  type MediaStatus,
  type MediaSummary,
} from '@/api/media'

const filters = ref<{
  q: string
  status: '' | MediaStatus
  sort: MediaSort
}>({ q: '', status: '', sort: 'CREATED_DESC' })
const view = ref<'grid' | 'list'>('grid')
const page = ref(1)
const pageSize = 20
const items = ref<MediaSummary[]>([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const policy = ref<MediaPolicy | null>(null)

const selectedId = ref('')
const detail = ref<MediaDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

let loadSequence = 0
let detailSequence = 0
let searchTimer: number | undefined

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const hasFilters = computed(() => Boolean(filters.value.q || filters.value.status))

onMounted(() => {
  void loadPolicy()
  void load()
})

watch(
  () => filters.value.q,
  () => {
    if (searchTimer !== undefined) window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(() => {
      page.value = 1
      void load()
    }, 250)
  },
)

watch(
  () => [filters.value.status, filters.value.sort],
  () => {
    page.value = 1
    void load()
  },
)

async function loadPolicy() {
  try {
    policy.value = await fetchMediaPolicy()
  } catch {
    // Policy is supplementary on this page; list errors are shown separately.
  }
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await fetchMediaList({
      q: filters.value.q.trim() || undefined,
      status: filters.value.status || undefined,
      page: page.value,
      pageSize,
      sort: filters.value.sort,
    })
    if (sequence !== loadSequence) return
    items.value = result.items
    total.value = result.total
  } catch (error) {
    if (sequence !== loadSequence) return
    items.value = []
    total.value = 0
    errorMessage.value = error instanceof AuthApiError
      ? error.message
      : '无法加载素材列表。'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || loading.value) return
  page.value = next
  void load()
}

function clearFilters() {
  filters.value.q = ''
  filters.value.status = ''
  filters.value.sort = 'CREATED_DESC'
}

async function openDetail(item: MediaSummary) {
  selectedId.value = item.mediaId
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  const sequence = ++detailSequence

  try {
    const result = await fetchMediaDetail(item.mediaId)
    if (sequence !== detailSequence) return
    detail.value = result
  } catch (error) {
    if (sequence !== detailSequence) return
    detailError.value = error instanceof AuthApiError
      ? error.message
      : '无法加载素材详情。'
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

function closeDetail() {
  detailSequence += 1
  selectedId.value = ''
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
}

function statusLabel(status: MediaStatus) {
  if (status === 'READY') return '可播放'
  if (status === 'FAILED') return '处理失败'
  return '处理中'
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDuration(value: number | null) {
  if (value === null || value < 0) return '待探测'
  const seconds = Math.floor(value / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
  }
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function formatPolicyLimit() {
  return policy.value ? formatBytes(policy.value.maxFileBytes) : '—'
}
</script>

<template>
  <section class="page media-page">
    <header class="page-header media-header">
      <div>
        <p class="eyebrow">PRIVATE MEDIA</p>
        <h1>素材库</h1>
        <p class="lead">
          只显示当前账号自己的素材。他人素材不可见，也不会通过 ID 泄露。
        </p>
      </div>
      <button class="primary" type="button" disabled title="上传将在素材阶段 2 接入">
        添加视频
      </button>
    </header>

    <section class="media-toolbar" aria-label="素材筛选">
      <div class="media-search">
        <label for="media-search">搜索</label>
        <input
          id="media-search"
          v-model="filters.q"
          type="search"
          maxlength="100"
          placeholder="按素材名称搜索"
        >
      </div>
      <label class="media-select">
        状态
        <select v-model="filters.status">
          <option value="">全部</option>
          <option value="READY">可播放</option>
          <option value="PROBE_PENDING">处理中</option>
          <option value="FAILED">处理失败</option>
        </select>
      </label>
      <label class="media-select">
        排序
        <select v-model="filters.sort">
          <option value="CREATED_DESC">最近添加</option>
          <option value="NAME_ASC">名称</option>
        </select>
      </label>
      <div class="media-view" role="group" aria-label="视图">
        <button type="button" :aria-pressed="view === 'grid'" @click="view = 'grid'">网格</button>
        <button type="button" :aria-pressed="view === 'list'" @click="view = 'list'">列表</button>
      </div>
    </section>

    <p class="media-policy-hint">
      单文件上限 {{ formatPolicyLimit() }}；当前策略 {{ policy?.policyVersion || '加载中' }}。
      上传、播放和删除将在后续阶段接入。
    </p>

    <section v-if="errorMessage" class="media-state media-error" role="alert">
      <h2>加载失败</h2>
      <p>{{ errorMessage }}</p>
      <button class="primary" type="button" @click="load">重试</button>
    </section>

    <section v-else-if="loading && items.length === 0" class="media-state" aria-live="polite">
      <h2>正在加载素材</h2>
      <p>正在读取当前账号的素材列表。</p>
    </section>

    <section v-else-if="items.length === 0" class="media-state media-empty">
      <h2>{{ hasFilters ? '没有符合条件的素材' : '还没有素材' }}</h2>
      <p v-if="hasFilters">调整搜索或筛选条件后再试。</p>
      <p v-else>上传功能将在素材阶段 2 接入；现在不会伪造演示素材。</p>
      <button v-if="hasFilters" class="primary" type="button" @click="clearFilters">清除筛选</button>
    </section>

    <section v-else :class="['media-results', view]" aria-live="polite">
      <article
        v-for="item in items"
        :key="item.mediaId"
        class="media-card"
        tabindex="0"
        role="button"
        @click="openDetail(item)"
        @keydown.enter="openDetail(item)"
      >
        <div class="media-cover" aria-hidden="true">
          <span>{{ item.displayName.slice(0, 1).toUpperCase() }}</span>
        </div>
        <div class="media-card-body">
          <div class="media-card-title">
            <h2>{{ item.displayName }}</h2>
            <span :class="['media-status', item.status.toLowerCase()]">
              {{ statusLabel(item.status) }}
            </span>
          </div>
          <p class="media-meta">
            {{ formatDuration(item.durationMs) }} · {{ formatBytes(item.sizeBytes) }}
          </p>
          <p class="media-meta">{{ formatDate(item.createdAt) }}</p>
        </div>
      </article>
    </section>

    <nav v-if="!errorMessage && total > 0" class="media-pagination" aria-label="素材分页">
      <button type="button" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 个素材</span>
      <button type="button" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">下一页</button>
    </nav>
  </section>

  <div v-if="selectedId" class="media-drawer-backdrop" @click.self="closeDetail">
    <aside class="media-drawer" role="dialog" aria-modal="true" aria-labelledby="media-detail-title">
      <header class="media-drawer-header">
        <div>
          <p class="eyebrow">素材详情</p>
          <h2 id="media-detail-title">{{ detail?.displayName || selectedId }}</h2>
        </div>
        <button class="media-close" type="button" aria-label="关闭详情" @click="closeDetail">关闭</button>
      </header>

      <p v-if="detailLoading" class="media-state-text">正在加载详情…</p>
      <p v-else-if="detailError" class="media-state-text media-error-text" role="alert">{{ detailError }}</p>

      <template v-else-if="detail">
        <div class="media-detail-hero" aria-hidden="true">
          <span>{{ detail.displayName.slice(0, 1).toUpperCase() }}</span>
        </div>

        <dl class="media-facts">
          <div><dt>状态</dt><dd>{{ statusLabel(detail.status) }}</dd></div>
          <div><dt>时长</dt><dd>{{ formatDuration(detail.durationMs) }}</dd></div>
          <div><dt>大小</dt><dd>{{ formatBytes(detail.sizeBytes) }}</dd></div>
          <div><dt>版本</dt><dd>{{ detail.version }}</dd></div>
          <div><dt>原始名称</dt><dd>{{ detail.originalName }}</dd></div>
          <div><dt>容器/编码</dt><dd>{{ detail.container || '待探测' }} / {{ detail.videoCodec || '待探测' }}</dd></div>
          <div><dt>分辨率</dt><dd>{{ detail.width && detail.height ? `${detail.width} × ${detail.height}` : '待探测' }}</dd></div>
          <div><dt>处理任务</dt><dd>{{ detail.processingJobId || '暂无' }}</dd></div>
          <div><dt>允许动作</dt><dd>{{ detail.allowedActions.join(' / ') }}</dd></div>
          <div><dt>可删除</dt><dd>{{ detail.deletion.canDelete ? '是' : '否' }}</dd></div>
        </dl>

        <section v-if="detail.failure" class="media-failure" role="alert">
          <h3>处理失败</h3>
          <p>{{ detail.failure.message }}</p>
          <code>{{ detail.failure.code }}</code>
        </section>

        <section v-if="!detail.deletion.canDelete" class="media-blockers">
          <h3>暂不可删除</h3>
          <p>该素材仍在处理中或存在引用：{{ detail.deletion.blockers.join(', ') || '处理中' }}</p>
        </section>

        <p class="media-policy-hint">
          播放将在素材阶段 4 接入；删除将在阶段 5 接入。当前详情只做真实查询。
        </p>
      </template>
    </aside>
  </div>
</template>

<style scoped>
.media-page {
  gap: 16px;
}

.media-header {
  display: flex;
  max-width: none;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.media-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
}

.media-search {
  display: grid;
  flex: 1 1 220px;
  gap: 6px;
  font-size: 13px;
  font-weight: 650;
}

.media-search input,
.media-select select {
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  color: var(--text);
  font: inherit;
}

.media-select {
  display: grid;
  gap: 6px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 650;
}

.media-select select {
  min-width: 120px;
}

.media-view {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 9px;
}

.media-view button {
  padding: 6px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.media-view button[aria-pressed='true'] {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 650;
}

.media-policy-hint {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.media-state {
  padding: 42px 20px;
  border: 1px dashed var(--line);
  border-radius: 14px;
  text-align: center;
}

.media-state h2 {
  margin: 0 0 8px;
}

.media-state p {
  margin: 0 auto 14px;
  max-width: 520px;
  color: var(--muted);
}

.media-error {
  border-color: #e0b4ae;
  background: #fff8f6;
}

.media-results.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.media-results.list {
  display: grid;
  gap: 10px;
}

.media-card {
  display: flex;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
  cursor: pointer;
  text-align: left;
  transition: border-color 120ms ease, transform 120ms ease;
}

.media-card:hover,
.media-card:focus-visible {
  border-color: #b8aaf5;
  transform: translateY(-1px);
}

.media-results.grid .media-card {
  flex-direction: column;
}

.media-results.list .media-card {
  align-items: center;
}

.media-cover {
  display: grid;
  min-height: 116px;
  flex: 0 0 116px;
  place-items: center;
  background:
    radial-gradient(circle at 30% 25%, rgb(255 178 36 / 34%), transparent 40%),
    linear-gradient(145deg, #262040, #4c3c8c);
  color: #fff;
  font-size: 34px;
  font-weight: 750;
}

.media-results.list .media-cover {
  min-height: 76px;
  flex-basis: 76px;
  font-size: 24px;
}

.media-card-body {
  min-width: 0;
  padding: 13px 14px;
}

.media-card-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.media-card-title h2 {
  margin: 0;
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-status {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 650;
}

.media-status.ready {
  background: #eaf7ef;
  color: var(--ok);
}

.media-status.failed {
  background: #fff1ef;
  color: var(--error);
}

.media-meta {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.media-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--muted);
  font-size: 13px;
}

.media-pagination button,
.media-close {
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
}

.media-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.media-drawer-backdrop {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  background: rgb(20 16 40 / 42%);
}

.media-drawer {
  width: min(100%, 480px);
  height: 100%;
  overflow: auto;
  padding: 22px;
  background: var(--panel);
  box-shadow: -18px 0 44px rgb(20 16 40 / 20%);
}

.media-drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.media-drawer-header h2 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 22px;
}

.media-detail-hero {
  display: grid;
  min-height: 190px;
  place-items: center;
  border-radius: 12px;
  background:
    radial-gradient(circle at 30% 25%, rgb(255 178 36 / 30%), transparent 40%),
    linear-gradient(145deg, #262040, #4c3c8c);
  color: #fff;
  font-size: 54px;
  font-weight: 750;
}

.media-facts {
  display: grid;
  gap: 9px;
  margin: 18px 0;
}

.media-facts div {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}

.media-facts dt {
  color: var(--muted);
}

.media-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.media-failure,
.media-blockers {
  margin: 14px 0;
  padding: 12px;
  border-radius: 10px;
  background: #fff8f6;
}

.media-failure h3,
.media-blockers h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.media-failure p,
.media-blockers p {
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.media-failure code {
  font-size: 12px;
}

.media-state-text,
.media-error-text {
  color: var(--muted);
  font-size: 14px;
}

.media-error-text {
  color: var(--error);
}

@media (max-width: 700px) {
  .media-header {
    flex-direction: column;
  }

  .media-header .primary {
    width: 100%;
  }

  .media-results.grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  }

  .media-drawer {
    width: 100%;
  }
}
</style>