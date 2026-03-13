// 管理员专属路由，统一挂在系统管理菜单下。
export const adminRoutes = [
  {
    // 用户与权限管理页面。
    path: '/admin/users',
    component: () => import('@/modules/admin-user/pages/UserManagementPage.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
  },
  {
    // 质控规则中心页面。
    path: '/admin/qc-rules',
    component: () => import('@/modules/qcrule/pages/QcRuleCenterPage.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
  },
]
