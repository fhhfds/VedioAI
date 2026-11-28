<template>
  <section class="page" aria-labelledby="forbidden-heading">
    <header class="page-header">
      <p class="eyebrow">403</p>
      <h1 id="forbidden-heading">无权访问</h1>
      <p class="lead">当前账号没有访问该页面的权限。如果权限刚刚变更，请重新登录。</p>
    </header>
    <div>
      <RouterLink class="primary" :to="{ name: 'media' }">返回素材库</RouterLink>
    </div>
  </section>
</template>