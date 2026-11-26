<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <section class="page" aria-labelledby="account-heading">
    <header class="page-header">
      <h1 id="account-heading">账户安全</h1>
      <p v-if="auth.session.status === 'unknown'" class="lead" role="status">正在确认当前会话</p>
      <p v-else-if="auth.session.status === 'anonymous'" class="lead">
        当前未登录。以下是账户安全页面的静态预览。
      </p>
      <p v-else class="lead">{{ auth.session.account.user.nickname }}的账户</p>
    </header>

    <div class="grid">
      <section class="card">
        <h2>个人资料</h2>
        <p>登录后显示昵称和脱敏邮箱。</p>
      </section>
      <section class="card">
        <h2>修改密码</h2>
        <p>修改密码成功后，所有设备都需要重新登录。</p>
      </section>
      <section class="card">
        <h2>登录设备</h2>
        <p>会话服务接入后可查看登录设备并撤销会话。退出全部设备也会退出当前设备。</p>
      </section>
    </div>

    <div>
      <RouterLink class="primary" :to="{ name: 'login' }">预览登录页</RouterLink>
    </div>
  </section>
</template>
