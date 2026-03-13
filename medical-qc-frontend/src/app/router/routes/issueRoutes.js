export const issueRoutes = [
  {
    path: '/issues',
    component: () => import('@/modules/issue/pages/IssueSummaryPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
]
