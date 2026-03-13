import { getCurrentUser } from '@/modules/auth/api/authApi'
import { clearAuthState, saveUserInfo } from '@/utils/auth'
import { hasRoleAccess, resolveDefaultRouteForRole } from '@/utils/permission'

export const fetchCurrentUserFromServer = async (requestConfig = {}) => {
  try {
    const currentUser = await getCurrentUser(requestConfig)
    return saveUserInfo(currentUser)
  } catch {
    clearAuthState()
    return null
  }
}

export const installAuthGuards = (router) => {
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
}
