// src/router/index.js
/**
 * @file src/router/index.js
 * @description 前端路由配置模块。
 * 主要功能：
 * 1. 定义应用的公开路由、受保护路由与角色受限路由。
 * 2. 在路由守卫中统一处理登录状态校验与页面级权限控制。
 * 3. 对医生/管理员分别提供差异化工作台访问入口。
 */

import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '@/api/auth'
import { clearAuthState, saveUserInfo } from '@/utils/auth'
import { hasRoleAccess, resolveDefaultRouteForRole } from '@/utils/permission'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true },
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/views/errors/Forbidden.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/',
    component: () => import('@/views/layout/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: '/dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
      },
      {
        path: '/head',
        component: () => import('@/views/quality/Head.vue'),
        meta: { requiresAuth: true, roles: ['doctor'] },
      },
      {
        path: '/chest-non-contrast',
        component: () => import('@/views/quality/ChestNonContrast.vue'),
        meta: { requiresAuth: true, roles: ['doctor'] },
      },
      {
        path: '/chest-contrast',
        component: () => import('@/views/quality/ChestContrast.vue'),
        meta: { requiresAuth: true, roles: ['doctor'] },
      },
      {
        path: '/coronary-cta',
        component: () => import('@/views/quality/CoronaryCTA.vue'),
        meta: { requiresAuth: true, roles: ['doctor'] },
      },
      {
        path: '/hemorrhage',
        component: () => import('@/views/quality/Hemorrhage.vue'),
        meta: { requiresAuth: true, roles: ['doctor'] },
      },
      {
        path: '/issues',
        component: () => import('@/views/summary/index.vue'),
        meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
      },
      {
        path: '/admin/users',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { requiresAuth: true, roles: ['admin'] },
      },
    ],
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

/**
 * 回源后端会话接口，确认当前浏览器 Session 是否仍然有效。
 *
 * @param {Object} requestConfig Axios 额外配置
 * @returns {Promise<Object | null>} 当前登录用户
 */
const fetchCurrentUserFromServer = async (requestConfig = {}) => {
  try {
    const currentUser = await getCurrentUser(requestConfig)
    return saveUserInfo(currentUser)
  } catch {
    clearAuthState()
    return null
  }
}

router.beforeEach(async (to, from, next) => {
  const isPublicPage = to.matched.some((record) => record.meta.public)
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const isLoginPage = to.path === '/login'
  const isRegisterPage = to.path === '/register'
  const loginPageMode = typeof to.query.mode === 'string' ? to.query.mode : ''
  const skipLoginRedirect = isLoginPage && ['switch', 'logout', 'expired', 'permission-updated'].includes(loginPageMode)

  if (isPublicPage) {
    if (isRegisterPage || !isLoginPage || skipLoginRedirect) {
      next()
      return
    }

    const currentUser = await fetchCurrentUserFromServer({ skipAuthRedirect: true })
    if (currentUser) {
      next(resolveDefaultRouteForRole(currentUser.role))
      return
    }

    next()
    return
  }

  if (!requiresAuth) {
    next()
    return
  }

  const currentUser = await fetchCurrentUserFromServer({ skipAuthRedirect: true })
  if (!currentUser) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!hasRoleAccess(to.meta.roles, currentUser.role)) {
    next({ path: '/forbidden', query: { from: to.fullPath } })
    return
  }

  next()
})

export default router

