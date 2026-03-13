import { DEFAULT_ROLE, getStoredUserInfo, isSupportedRole } from '@/utils/auth'

/**
 * 路由与菜单权限配置。
 * 说明：医生侧聚焦影像质控工作流；管理员侧聚焦全局监控与账号治理。
 */
export const ROLE_MENU_CONFIG = [
  {
    type: 'item',
    index: '/dashboard',
    label: '首页',
    icon: 'House',
    roles: ['doctor', 'admin'],
  },
  {
    type: 'group',
    index: 'quality-group',
    label: '影像质控',
    icon: 'DocumentRemove',
    roles: ['doctor'],
    children: [
      { index: '/head', label: 'CT头部平扫', roles: ['doctor'] },
      { index: '/hemorrhage', label: '头部出血检测', roles: ['doctor'] },
      { index: '/chest-non-contrast', label: 'CT胸部平扫', roles: ['doctor'] },
      { index: '/chest-contrast', label: 'CT胸部增强', roles: ['doctor'] },
      { index: '/coronary-cta', label: '冠脉CTA', roles: ['doctor'] },
    ],
  },
  {
    type: 'group',
    index: 'patient-info-group',
    label: '患者信息管理',
    icon: 'UserFilled',
    roles: ['doctor', 'admin'],
    children: [
      { index: '/patient-info/head', label: 'CT头部平扫患者', roles: ['doctor', 'admin'] },
      { index: '/patient-info/hemorrhage', label: '头部出血患者', roles: ['doctor', 'admin'] },
      { index: '/patient-info/chest-non-contrast', label: 'CT胸部平扫患者', roles: ['doctor', 'admin'] },
      { index: '/patient-info/chest-contrast', label: 'CT胸部增强患者', roles: ['doctor', 'admin'] },
      { index: '/patient-info/coronary-cta', label: '冠脉CTA患者', roles: ['doctor', 'admin'] },
    ],
  },
  {
    type: 'item',
    index: '/issues',
    label: '异常汇总',
    icon: 'Warning',
    roles: ['doctor', 'admin'],
  },
  {
    type: 'item',
    index: '/quality-tasks',
    label: '质控任务中心',
    icon: 'List',
    roles: ['doctor', 'admin'],
  },
  {
    type: 'group',
    index: 'admin-group',
    label: '系统管理',
    icon: 'Setting',
    roles: ['admin'],
    children: [
      { index: '/admin/users', label: '用户与权限', roles: ['admin'] },
      { index: '/admin/qc-rules', label: '质控规则中心', roles: ['admin'] },
    ],
  },
]

/**
 * 计算当前角色是否可访问指定角色列表。
 *
 * @param {string[] | undefined} roles 允许访问的角色列表
 * @param {string} role 当前角色
 * @returns {boolean} 是否允许访问
 */
export const hasRoleAccess = (roles, role = DEFAULT_ROLE) => {
  if (!Array.isArray(roles) || roles.length === 0) {
    // 未显式声明角色限制时，默认所有已登录用户都可访问。
    return true
  }

  return roles.includes(role)
}

/**
 * 获取当前登录用户角色。
 *
 * @returns {string} 当前角色
 */
export const getCurrentRole = () => {
  // 菜单和默认路由都只依赖缓存中的标准化角色信息。
  const role = getStoredUserInfo()?.role
  return isSupportedRole(role) ? role : DEFAULT_ROLE
}

/**
 * 根据角色解析默认首页。
 *
 * @param {string} role 当前角色
 * @returns {string} 默认跳转路由
 */
export const resolveDefaultRouteForRole = (role = DEFAULT_ROLE) => {
  // 当前两个角色都落到首页，但保留函数用于未来扩展角色首页差异。
  return role === 'admin' ? '/dashboard' : '/dashboard'
}

/**
 * 获取指定角色可见的菜单结构。
 *
 * @param {string} role 当前角色
 * @returns {Array} 过滤后的菜单配置
 */
export const getMenusByRole = (role = DEFAULT_ROLE) => {
  return ROLE_MENU_CONFIG.filter((section) => hasRoleAccess(section.roles, role)).map((section) => {
    if (!Array.isArray(section.children)) {
      return section
    }

    // 分组菜单需要继续过滤子项，避免管理员/医生看到越权入口。
    return {
      ...section,
      children: section.children.filter((child) => hasRoleAccess(child.roles, role)),
    }
  })
}
