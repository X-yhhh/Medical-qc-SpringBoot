import { createRouter, createWebHistory } from 'vue-router'
import { installAuthGuards } from './guards'
import { publicRoutes } from './routes/publicRoutes'
import { qualityRoutes } from './routes/qualityRoutes'
import { patientRoutes } from './routes/patientRoutes'
import { issueRoutes } from './routes/issueRoutes'
import { adminRoutes } from './routes/adminRoutes'

const protectedChildRoutes = [
  { path: '', redirect: '/dashboard' },
  ...qualityRoutes,
  ...patientRoutes,
  ...issueRoutes,
  ...adminRoutes,
]

const routes = [
  ...publicRoutes,
  {
    path: '/',
    component: () => import('@/modules/app-shell/pages/AppLayoutPage.vue'),
    meta: { requiresAuth: true },
    children: protectedChildRoutes,
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

installAuthGuards(router)

export default router
