export const adminRoutes = [
  {
    path: '/admin/users',
    component: () => import('@/modules/admin-user/pages/UserManagementPage.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
  },
  {
    path: '/admin/qc-rules',
    component: () => import('@/modules/qcrule/pages/QcRuleCenterPage.vue'),
    meta: { requiresAuth: true, roles: ['admin'] },
  },
]
