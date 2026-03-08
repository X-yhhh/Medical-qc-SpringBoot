import { Setting, UserFilled } from '@element-plus/icons-vue'

export const DEFAULT_ROLE = 'doctor'

export const ROLE_OPTIONS = [
  {
    value: 'doctor',
    label: '医生',
    description: '执行影像质控、病例审核与结果确认',
    icon: UserFilled,
    roleId: 2,
  },
  {
    value: 'admin',
    label: '管理员',
    description: '管理账号、流程配置与系统运营',
    icon: Setting,
    roleId: 1,
  },
]

const roleMap = ROLE_OPTIONS.reduce((accumulator, option) => {
  accumulator[option.value] = option
  return accumulator
}, {})

export const isSupportedRole = (role) => typeof role === 'string' && Boolean(roleMap[role])

export const getRoleMeta = (role = DEFAULT_ROLE) => roleMap[role] || roleMap[DEFAULT_ROLE]

export const normalizeUserInfo = (userInfo) => {
  if (!userInfo || typeof userInfo !== 'object' || Array.isArray(userInfo)) {
    return null
  }

  const role = isSupportedRole(userInfo.role) ? userInfo.role : DEFAULT_ROLE
  const roleMeta = getRoleMeta(role)

  return {
    id: userInfo.id || null,
    username: userInfo.username || '',
    fullName: userInfo.fullName || userInfo.full_name || '',
    role,
    roleId: userInfo.roleId || roleMeta.roleId,
    roleLabel: userInfo.roleLabel || roleMeta.label,
  }
}

export const saveUserInfo = (userInfo) => {
  const normalizedUserInfo = normalizeUserInfo(userInfo)
  if (!normalizedUserInfo) {
    sessionStorage.removeItem('user_info')
    return null
  }

  sessionStorage.setItem('user_info', JSON.stringify(normalizedUserInfo))
  return normalizedUserInfo
}

export const getStoredUserInfo = () => {
  const rawUserInfo = sessionStorage.getItem('user_info')
  if (!rawUserInfo) {
    return null
  }

  try {
    return normalizeUserInfo(JSON.parse(rawUserInfo))
  } catch {
    sessionStorage.removeItem('user_info')
    return null
  }
}

export const clearAuthState = () => {
  sessionStorage.removeItem('access_token')
  sessionStorage.removeItem('user_info')
}
