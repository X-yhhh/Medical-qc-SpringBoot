import request from '@/utils/request'

// 获取管理员用户列表，支持分页和筛选参数透传。
export const getAdminUsers = (params) => {
  return request({
    url: '/admin/users',
    method: 'get',
    params,
  })
}

// 更新指定管理员或医生账号的角色、状态等信息。
export const updateAdminUser = (userId, data) => {
  return request({
    url: `/admin/users/${userId}`,
    method: 'put',
    data,
  })
}

// 获取质控规则列表。
export const getAdminQcRules = (params) => {
  return request({
    url: '/admin/qc-rules',
    method: 'get',
    params,
  })
}

// 新增一条质控规则配置。
export const createAdminQcRule = (data) => {
  return request({
    url: '/admin/qc-rules',
    method: 'post',
    data,
  })
}

// 更新现有质控规则。
export const updateAdminQcRule = (ruleId, data) => {
  return request({
    url: `/admin/qc-rules/${ruleId}`,
    method: 'put',
    data,
  })
}
