<script setup lang="ts">
const links = [
  { to: '/media', label: '素材库' },
  { to: '/narration', label: 'AI 解说' },
  { to: '/highlights', label: '高光打点' },
  { to: '/artifacts', label: '成果库' },
] as const
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink class="brand" to="/media">VedioAI</RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="nav-link"
        >
          {{ link.label }}
        </RouterLink>
      </nav>

      <span
        class="task-entry"
        title="任务服务待 P3 接入"
        aria-label="任务服务待 P3 接入"
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
        <span>任务</span>
        <span class="task-stage">P3</span>
      </span>
    </header>

    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 24px;
  min-height: 58px;
  border-bottom: 1px solid var(--line);
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(8px);
}

.brand {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
}

.nav-link {
  padding: 7px 12px;
  border-radius: 8px;
  color: var(--muted);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.nav-link:hover {
  background: var(--bg);
  color: var(--text);
}

.nav-link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}

.task-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  cursor: default;
}

.task-stage {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.content {
  width: min(1120px, 100%);
  margin: 0 auto;
  padding: 28px 24px 48px;
}

@media (max-width: 720px) {
  .topbar {
    flex-wrap: wrap;
    gap: 8px 16px;
    padding: 10px 16px;
  }

  .nav {
    order: 3;
    width: 100%;
  }

  .task-entry {
    margin-left: auto;
  }

  .content {
    padding: 20px 16px 36px;
  }
}
</style>