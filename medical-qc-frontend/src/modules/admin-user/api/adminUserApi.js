import request from '@/utils/request'

export const getAdminUsers = (params) => {
  return request({
    url: '/admin/users',
    method: 'get',
    params,
  })
}

export const updateAdminUser = (userId, data) => {
  return request({
    url: `/admin/users/${userId}`,
    method: 'put',
    data,
  })
}

export const getAdminQcRules = (params) => {
  return request({
    url: '/admin/qc-rules',
    method: 'get',
    params,
  })
}

export const createAdminQcRule = (data) => {
  return request({
    url: '/admin/qc-rules',
    method: 'post',
    data,
  })
}

export const updateAdminQcRule = (ruleId, data) => {
  return request({
    url: `/admin/qc-rules/${ruleId}`,
    method: 'put',
    data,
  })
}
