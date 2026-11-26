<script setup lang="ts">
import { ref } from 'vue'

import { checkHealth, type HealthResponse } from '@/api/health'

type CheckState = 'idle' | 'checking' | 'success' | 'error'

const state = ref<CheckState>('idle')
const result = ref<HealthResponse | null>(null)
const errorMessage = ref('')

async function runCheck() {
  if (state.value === 'checking') {
    return
  }

  state.value = 'checking'
  result.value = null
  errorMessage.value = ''

  try {
    result.value = await checkHealth()
    state.value = 'success'
  } catch (error) {
    state.value = 'error'
    errorMessage.value = error instanceof Error ? error.message : String(error)
  }
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <p class="eyebrow">P1 开发骨架</p>
      <h1>素材库</h1>
      <p class="lead">
        素材上传、私有播放与列表将在 P3 接入。当前页面只验证前端、代理与 Java 后端的基本连接。
      </p>
    </header>

    <div class="grid">
      <article class="card">
        <h2>后端连接自检</h2>
        <p>通过 Vite 的 <code>/api</code> 代理请求 Java 后端的存活接口。</p>
        <button class="primary" type="button" :disabled="state === 'checking'" @click="runCheck">
          {{ state === 'checking' ? '检查中…' : '检查后端连接' }}
        </button>

        <p v-if="state === 'success'" class="status ok">
          连接成功：{{ result?.app }} / {{ result?.status }}
        </p>
        <p v-else-if="state === 'error'" class="status error">
          连接失败：{{ errorMessage }}
        </p>
        <p v-else class="status muted">尚未检查。</p>

        <p class="note">
          健康响应只说明 HTTP 可达，不代表数据库、对象存储或云模型已经就绪。
        </p>
      </article>

      <article class="card empty">
        <h2>素材列表</h2>
        <p>素材库将在 P3 接入真实数据。</p>
      </article>
    </div>
  </section>
</template>
