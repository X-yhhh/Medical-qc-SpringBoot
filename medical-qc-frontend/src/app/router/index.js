import { createRouter, createWebHistory } from 'vue-router'
import { installAuthGuards } from './guards'
import { publicRoutes } from './routes/publicRoutes'
import { qualityRoutes } from './routes/qualityRoutes'
import { patientRoutes } from './routes/patientRoutes'
import { issueRoutes } from './routes/issueRoutes'
import { adminRoutes } from './routes/adminRoutes'

// 需要登录的业务子路由统一挂载在根布局下，复用同一套导航和页面壳。
const protectedChildRoutes = [
  { path: '', redirect: '/dashboard' },
  ...qualityRoutes,
  ...patientRoutes,
  ...issueRoutes,
  ...adminRoutes,
]

// 路由表同时承载公共页面、受保护布局页和兜底跳转规则。
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

// 使用 HTML5 History 模式，保证地址栏与后端代理规则一致。
const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由实例创建完成后立刻安装全局守卫，确保首屏导航也受鉴权控制。
installAuthGuards(router)

export default router
