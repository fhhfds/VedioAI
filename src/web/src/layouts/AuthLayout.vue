<template>
  <div class="auth-layout">
    <aside class="auth-brand">
      <RouterLink class="auth-wordmark" :to="{ name: 'media' }">
        <span class="auth-brand-mark" aria-hidden="true"></span>
        VedioAI
      </RouterLink>

      <div class="auth-brand-content">
        <p class="auth-brand-title">视频创作<br>工作台</p>
        <p class="auth-brand-caption">一次上传，在同一份素材上完成分析、高光和解说。</p>
        <div class="auth-reel" aria-hidden="true">
          <div class="auth-reel-ruler"><span>00:00</span><span>00:15</span><span>00:30</span><span>00:45</span></div>
          <div class="auth-reel-lane"><span></span><span></span><span></span></div>
          <div class="auth-reel-lane"><span></span><span></span></div>
          <div class="auth-reel-lane"><span></span><span></span><span></span></div>
          <i class="auth-reel-playhead"></i>
        </div>
      </div>
    </aside>

    <main class="auth-main">
      <RouterLink class="auth-wordmark auth-wordmark-mobile" :to="{ name: 'media' }">
        <span class="auth-brand-mark" aria-hidden="true"></span>
        VedioAI
      </RouterLink>
      <section class="auth-panel" aria-labelledby="auth-heading">
        <p class="auth-preview">账号服务已接入 · 登录、注册与密码找回可用</p>
        <RouterView />
      </section>
      <RouterLink class="auth-return" :to="{ name: 'media' }">返回素材库</RouterLink>
    </main>
  </div>
</template>
