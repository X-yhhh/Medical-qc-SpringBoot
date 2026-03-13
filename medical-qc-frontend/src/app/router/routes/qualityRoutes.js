export const qualityRoutes = [
  {
    path: '/dashboard',
    component: () => import('@/modules/dashboard/pages/DashboardPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
  {
    path: '/head',
    component: () => import('@/modules/qctask/pages/HeadQualityPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    path: '/chest-non-contrast',
    component: () => import('@/modules/qctask/pages/ChestNonContrastPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    path: '/chest-contrast',
    component: () => import('@/modules/qctask/pages/ChestContrastPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    path: '/coronary-cta',
    component: () => import('@/modules/qctask/pages/CoronaryCtaPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    path: '/hemorrhage',
    component: () => import('@/modules/qctask/pages/HemorrhagePage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    path: '/quality-tasks',
    component: () => import('@/modules/qctask/pages/QualityTaskCenterPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
]
