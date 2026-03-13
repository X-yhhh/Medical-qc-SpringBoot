import { Setting, UserFilled } from '@element-plus/icons-vue'

// 未识别角色时统一回退为医生，保证页面逻辑有稳定默认值。
export const DEFAULT_ROLE = 'doctor'

// 角色元数据既服务于登录/注册选择器，也服务于用户信息标准化。
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

// 把角色数组转成哈希表，减少运行时反复遍历。
const roleMap = ROLE_OPTIONS.reduce((accumulator, option) => {
  accumulator[option.value] = option
  return accumulator
}, {})

// 判断前后端传入的 role 是否是系统支持的角色编码。
export const isSupportedRole = (role) => typeof role === 'string' && Boolean(roleMap[role])

// 读取角色元数据，缺省时回退到默认角色。
export const getRoleMeta = (role = DEFAULT_ROLE) => roleMap[role] || roleMap[DEFAULT_ROLE]

// 把接口返回或本地缓存中的用户对象整理成前端统一结构。
export const normalizeUserInfo = (userInfo) => {
  if (!userInfo || typeof userInfo !== 'object' || Array.isArray(userInfo)) {
    return null
  }

  // 后端返回的 role 可能缺失，前端统一补全角色和展示标签。
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

// 登录、刷新会话或权限校验成功后，把用户快照写入 sessionStorage。
export const saveUserInfo = (userInfo) => {
  const normalizedUserInfo = normalizeUserInfo(userInfo)
  if (!normalizedUserInfo) {
    // 非法用户对象直接清空缓存，避免前端继续使用脏数据。
    sessionStorage.removeItem('user_info')
    return null
  }

  sessionStorage.setItem('user_info', JSON.stringify(normalizedUserInfo))
  return normalizedUserInfo
}

// 从浏览器恢复用户缓存，并在读取时再次做结构标准化。
export const getStoredUserInfo = () => {
  const rawUserInfo = sessionStorage.getItem('user_info')
  if (!rawUserInfo) {
    return null
  }

  try {
    return normalizeUserInfo(JSON.parse(rawUserInfo))
  } catch {
    // 缓存损坏时立刻清理，避免守卫和页面读取异常。
    sessionStorage.removeItem('user_info')
    return null
  }
}

// 统一清理认证态缓存，供拦截器、登出和会话恢复失败场景复用。
export const clearAuthState = () => {
  sessionStorage.removeItem('access_token')
  sessionStorage.removeItem('user_info')
}
