// 公共路由不要求已登录，主要承载登录、注册和无权限页面。
export const publicRoutes = [
  {
    // 登录页。
    path: '/login',
    name: 'Login',
    component: () => import('@/modules/auth/pages/LoginPage.vue'),
    meta: { public: true },
  },
  {
    // 注册页。
    path: '/register',
    name: 'Register',
    component: () => import('@/modules/auth/pages/RegisterPage.vue'),
    meta: { public: true },
  },
  {
    // 无权限页本身仍要求已登录，避免未登录用户直接进入该页。
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/modules/app-shell/pages/ForbiddenPage.vue'),
    meta: { requiresAuth: true },
  },
]
