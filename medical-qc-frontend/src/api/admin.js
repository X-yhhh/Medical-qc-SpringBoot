// src/api/admin.js
// ----------------------------------------------------------------------------------
// 管理员用户与权限前端 API
// 作用：为“用户与权限”页面提供列表查询与更新能力。
// 对接后端：/api/v1/admin/users
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

/**
 * 获取用户分页列表。
 *
 * @param {Object} params 查询参数
 * @returns {Promise<Object>} 用户列表与统计摘要
 */
export const getAdminUsers = (params) => {
  return request({
    url: '/admin/users',
    method: 'get',
    params,
  })
}

/**
 * 更新指定用户信息。
 *
 * @param {number|string} userId 目标用户 ID
 * @param {Object} data 更新内容
 * @returns {Promise<Object>} 更新后的用户摘要
 */
export const updateAdminUser = (userId, data) => {
  return request({
    url: `/admin/users/${userId}`,
    method: 'put',
    data,
  })
}

/**
 * 获取质控规则分页列表。
 */
export const getAdminQcRules = (params) => {
  return request({
    url: '/admin/qc-rules',
    method: 'get',
    params,
  })
}

/**
 * 新增质控规则。
 */
export const createAdminQcRule = (data) => {
  return request({
    url: '/admin/qc-rules',
    method: 'post',
    data,
  })
}

/**
 * 更新质控规则。
 */
export const updateAdminQcRule = (ruleId, data) => {
  return request({
    url: `/admin/qc-rules/${ruleId}`,
    method: 'put',
    data,
  })
}
