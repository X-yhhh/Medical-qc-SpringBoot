// src/router/index.js
/**
 * @file src/router/index.js
 * @description 前端路由配置模块
 * 主要功能：
 * 1. 定义应用的所有路由规则（公开路由、需认证路由）。
 * 2. 实现路由懒加载，优化首屏加载速度。
 * 3. 配置全局路由守卫 (Navigation Guard)，实现登录状态拦截。
 */

import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '@/api/auth'

// ... existing routes definition ...
const routes = [
  // ----------------------------------------------------------------------------------
  // 公开路由 (Public Routes)
  // 无需登录即可访问
  // ----------------------------------------------------------------------------------
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true } // 标记为公开页面
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true }
  },

  // ----------------------------------------------------------------------------------
  // 受保护路由 (Protected Routes)
  // 需登录认证 (requiresAuth: true)
  // ----------------------------------------------------------------------------------
  {
    path: '/',
    component: () => import('@/views/layout/Layout.vue'), // 使用全局布局组件
    meta: { requiresAuth: true },
    children: [
      // 默认重定向到仪表盘
      { path: '', redirect: '/dashboard' },

      // 仪表盘
      {
        path: '/dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { requiresAuth: true }
      },

      // -------------------
      // 四大质控业务模块
      // -------------------
      {
        path: '/head', // CT 头部平扫
        component: () => import('@/views/quality/Head.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/chest-non-contrast', // CT 胸部平扫
        component: () => import('@/views/quality/ChestNonContrast.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/chest-contrast', // CT 胸部增强
        component: () => import('@/views/quality/ChestContrast.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/coronary-cta', // 冠脉 CTA
        component: () => import('@/views/quality/CoronaryCTA.vue'),
        meta: { requiresAuth: true }
      },

      // -------------------
      // 脑出血检测模块
      // -------------------
      {
        path: '/hemorrhage',
        component: () => import('@/views/quality/Hemorrhage.vue'),
        meta: { requiresAuth: true }
      },

      // -------------------
      // 异常汇总模块
      // -------------------
      {
        path: '/issues',
        component: () => import('@/views/summary/index.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },

  // ----------------------------------------------------------------------------------
  // 404 路由
  // 匹配所有未定义路径，重定向到仪表盘
  // ----------------------------------------------------------------------------------
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(), // 使用 HTML5 History 模式
  routes
})

// ----------------------------------------------------------------------------------
// 全局路由守卫
// 作用：在路由跳转前检查用户权限
// ----------------------------------------------------------------------------------
router.beforeEach(async (to, from, next) => {
  const requiresAuth = to.meta.requiresAuth !== false && !to.meta.public
  const publicPages = ['/login', '/register']
  const authRequired = !publicPages.includes(to.path)

  if (authRequired) {
    // 检查本地 Session 标记
    const userInfo = sessionStorage.getItem('user_info')

    if (userInfo) {
      // 即使本地有标记，为了安全，建议(可选)调用后端验证 Session 有效性
      // 这里为了流畅性，我们信任 sessionStorage，如果后端 401，拦截器会处理
      next()
    } else {
      // 尝试调用后端检查 Session 状态 (防止用户手动清除了 sessionStorage 但 Cookie 还在)
      try {
        await getCurrentUser()
        // 验证成功，补写 sessionStorage (可选)
        next()
      } catch (error) {
        next('/login')
      }
    }
  } else {
    // 访问登录页，如果已登录则跳转首页
    const userInfo = sessionStorage.getItem('user_info')
    if (userInfo && publicPages.includes(to.path)) {
      next('/')
    } else {
      next()
    }
  }
})

export default router
