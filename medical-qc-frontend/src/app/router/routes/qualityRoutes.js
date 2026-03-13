// 医疗质控主流程路由，覆盖看板、各任务入口和任务中心。
export const qualityRoutes = [
  {
    // 首页看板，医生和管理员共用。
    path: '/dashboard',
    component: () => import('@/modules/dashboard/pages/DashboardPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
  {
    // CT 头部平扫质控入口。
    path: '/head',
    component: () => import('@/modules/qctask/pages/HeadQualityPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    // CT 胸部平扫质控入口。
    path: '/chest-non-contrast',
    component: () => import('@/modules/qctask/pages/ChestNonContrastPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    // CT 胸部增强质控入口。
    path: '/chest-contrast',
    component: () => import('@/modules/qctask/pages/ChestContrastPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    // 冠脉 CTA 质控入口。
    path: '/coronary-cta',
    component: () => import('@/modules/qctask/pages/CoronaryCtaPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    // 头部出血检测入口。
    path: '/hemorrhage',
    component: () => import('@/modules/qctask/pages/HemorrhagePage.vue'),
    meta: { requiresAuth: true, roles: ['doctor'] },
  },
  {
    // 质控任务中心，供多角色查看提交结果与历史任务。
    path: '/quality-tasks',
    component: () => import('@/modules/qctask/pages/QualityTaskCenterPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
]
