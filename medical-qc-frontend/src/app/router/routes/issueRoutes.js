// 异常汇总与工单处理路由，医生和管理员都可以查看。
export const issueRoutes = [
  {
    path: '/issues',
    component: () => import('@/modules/issue/pages/IssueSummaryPage.vue'),
    meta: { requiresAuth: true, roles: ['doctor', 'admin'] },
  },
]
