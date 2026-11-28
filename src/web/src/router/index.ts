import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { safeRedirect } from './redirect'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/auth',
      component: () => import('@/layouts/AuthLayout.vue'),
      children: [
        { path: '', redirect: { name: 'login' } },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/pages/auth/LoginPage.vue'),
          meta: { guestOnly: true },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/pages/auth/RegisterPage.vue'),
          meta: { guestOnly: true },
        },
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
      meta: { requiresAuth: true },
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
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/pages/admin/AdminUsersPage.vue'),
          meta: { title: '账号管理', role: 'ADMIN' },
        },
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('@/pages/ForbiddenPage.vue'),
          meta: { title: '无权访问' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()

  if (to.meta.requiresAuth && auth.session.status !== 'authenticated') {
    const redirect = safeRedirect(to.fullPath)
    return {
      name: 'login',
      query: redirect ? { redirect } : undefined,
    }
  }

  if (to.meta.guestOnly && auth.session.status === 'authenticated') {
    return safeRedirect(to.query.redirect) ?? { name: 'media' }
  }

  if (typeof to.meta.role === 'string') {
    const role = to.meta.role
    if (role !== 'USER' && role !== 'ADMIN') return { name: 'forbidden' }
    if (!auth.hasRole(role)) return { name: 'forbidden' }
  }

  return true
})

export default router
