export const publicRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/modules/auth/pages/LoginPage.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/modules/auth/pages/RegisterPage.vue'),
    meta: { public: true },
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('@/modules/app-shell/pages/ForbiddenPage.vue'),
    meta: { requiresAuth: true },
  },
]
