import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/auth',
      component: () => import('@/layouts/AuthLayout.vue'),
      children: [
        { path: '', redirect: { name: 'login' } },
        { path: 'login', name: 'login', component: () => import('@/pages/auth/LoginPage.vue') },
        { path: 'register', name: 'register', component: () => import('@/pages/auth/RegisterPage.vue') },
        {
          path: 'forgot-password',
          name: 'forgot-password',
          component: () => import('@/pages/auth/ForgotPasswordPage.vue'),
        },
        {
          path: 'reset-password',
          name: 'reset-password',
          component: () => import('@/pages/auth/ResetPasswordPage.vue'),
        },
      ],
    },
    {
      path: '/',
      component: () => import('@/layouts/AppShell.vue'),
      children: [
        { path: '', redirect: { name: 'media' } },
        {
          path: 'media',
          name: 'media',
          component: () => import('@/pages/MediaLibraryPage.vue'),
          meta: { title: '素材库' },
        },
        {
          path: 'narration',
          name: 'narration',
          component: () => import('@/pages/NarrationStudioPage.vue'),
          meta: { title: 'AI 解说' },
        },
        {
          path: 'highlights',
          name: 'highlights',
          component: () => import('@/pages/HighlightReviewPage.vue'),
          meta: { title: '高光打点' },
        },
        {
          path: 'artifacts',
          name: 'artifacts',
          component: () => import('@/pages/ArtifactLibraryPage.vue'),
          meta: { title: '成果库' },
        },
        {
          path: 'account/security',
          name: 'account-security',
          component: () => import('@/pages/account/AccountSecurityPage.vue'),
          meta: { title: '账户安全' },
        },
      ],
    },
  ],
})

export default router
