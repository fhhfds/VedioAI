import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/media' },
    {
      path: '/media',
      name: 'media',
      component: () => import('@/pages/MediaLibraryPage.vue'),
    },
    {
      path: '/narration',
      name: 'narration',
      component: () => import('@/pages/NarrationStudioPage.vue'),
    },
    {
      path: '/highlights',
      name: 'highlights',
      component: () => import('@/pages/HighlightReviewPage.vue'),
    },
    {
      path: '/artifacts',
      name: 'artifacts',
      component: () => import('@/pages/ArtifactLibraryPage.vue'),
    },
  ],
})

export default router
