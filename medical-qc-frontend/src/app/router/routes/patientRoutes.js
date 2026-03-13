// 组装患者管理页的公共 meta，供同一个页面组件复用不同 taskType 配置。
const buildPatientRouteMeta = (taskType, pageTitle, pageDescription, workflowConnected) => ({
  requiresAuth: true,
  roles: ['doctor', 'admin'],
  taskType,
  pageTitle,
  pageDescription,
  workflowConnected,
})

// 五类患者信息管理路由都复用同一页面组件，通过 meta 区分业务类型和文案。
export const patientRoutes = [
  {
    // CT 头部平扫患者管理。
    path: '/patient-info/head',
    component: () => import('@/modules/patient/pages/QualityPatientManagementPage.vue'),
    meta: buildPatientRouteMeta(
      'head',
      'CT头部平扫患者信息管理',
      '维护 CT 头部平扫任务的患者基础档案与检查号信息。',
      false,
    ),
  },
  {
    // 头部出血检测患者管理，已与真实检测流程联动。
    path: '/patient-info/hemorrhage',
    component: () => import('@/modules/patient/pages/QualityPatientManagementPage.vue'),
    meta: buildPatientRouteMeta(
      'hemorrhage',
      '头部出血检测患者信息管理',
      '维护头部出血检测患者档案，并与真实检测及 PACS 患者信息联动。',
      true,
    ),
  },
  {
    // CT 胸部平扫患者管理。
    path: '/patient-info/chest-non-contrast',
    component: () => import('@/modules/patient/pages/QualityPatientManagementPage.vue'),
    meta: buildPatientRouteMeta(
      'chest-non-contrast',
      'CT胸部平扫患者信息管理',
      '维护 CT 胸部平扫任务的患者基础档案与检查号信息。',
      false,
    ),
  },
  {
    // CT 胸部增强患者管理。
    path: '/patient-info/chest-contrast',
    component: () => import('@/modules/patient/pages/QualityPatientManagementPage.vue'),
    meta: buildPatientRouteMeta(
      'chest-contrast',
      'CT胸部增强患者信息管理',
      '维护 CT 胸部增强任务的患者基础档案与检查号信息。',
      false,
    ),
  },
  {
    // 冠脉 CTA 患者管理。
    path: '/patient-info/coronary-cta',
    component: () => import('@/modules/patient/pages/QualityPatientManagementPage.vue'),
    meta: buildPatientRouteMeta(
      'coronary-cta',
      '冠脉CTA患者信息管理',
      '维护冠脉 CTA 任务的患者基础档案与检查号信息。',
      false,
    ),
  },
]
