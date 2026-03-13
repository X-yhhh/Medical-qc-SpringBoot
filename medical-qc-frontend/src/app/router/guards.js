import { getCurrentUser } from '@/modules/auth/api/authApi'
import { clearAuthState, saveUserInfo } from '@/utils/auth'
import { hasRoleAccess, resolveDefaultRouteForRole } from '@/utils/permission'

// 从服务端同步当前会话用户，统一处理本地缓存刷新与失效清理。
export const fetchCurrentUserFromServer = async (requestConfig = {}) => {
  try {
    // /auth/current 会触发后端 Session 二次校验，返回最新用户快照。
    const currentUser = await getCurrentUser(requestConfig)
    return saveUserInfo(currentUser)
  } catch {
    // 一旦拉取失败，说明本地缓存不再可信，需要立刻清空。
    clearAuthState()
    return null
  }
}

// 安装全局前置守卫，统一处理登录态恢复、角色鉴权和跳转策略。
export const installAuthGuards = (router) => {
  router.beforeEach(async (to, from, next) => {
    // 公共页不需要登录，但登录页仍会尝试恢复已有会话，避免重复登录。
    const isPublicPage = to.matched.some((record) => record.meta.public)
    // 需要鉴权的页面通常挂在根布局下，由 meta.requiresAuth 标记。
    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
    const isLoginPage = to.path === '/login'
    const isRegisterPage = to.path === '/register'
    // 某些登录页模式是由拦截器主动跳转过来的，不应再次触发重定向死循环。
    const loginPageMode = typeof to.query.mode === 'string' ? to.query.mode : ''
    const skipLoginRedirect = isLoginPage && ['switch', 'logout', 'expired', 'permission-updated'].includes(loginPageMode)

    if (isPublicPage) {
      // 注册页、非登录公共页或特殊模式登录页直接放行。
      if (isRegisterPage || !isLoginPage || skipLoginRedirect) {
        next()
        return
      }

      // 普通登录页如果检测到已有有效会话，直接回到该角色默认首页。
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

    // 进入受保护页面前必须先确认服务端 Session 仍然有效。
    const currentUser = await fetchCurrentUserFromServer({ skipAuthRedirect: true })
    if (!currentUser) {
      // 记录目标地址，方便登录成功后回跳。
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }

    // 角色不匹配时统一跳到无权限页，而不是停留在当前页面。
    if (!hasRoleAccess(to.meta.roles, currentUser.role)) {
      next({ path: '/forbidden', query: { from: to.fullPath } })
      return
    }

    // 会话有效且角色匹配时正常放行。
    next()
  })
}
