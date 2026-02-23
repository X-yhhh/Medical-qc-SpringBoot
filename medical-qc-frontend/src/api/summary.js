// src/api/summary.js
// ----------------------------------------------------------------------------------
// 异常汇总前端 API (Summary API)
// 作用：获取“异常汇总”页面的各类统计数据。
// 对接后端：/api/v1/summary
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

// 获取汇总统计数据 (Total, Today, Pending...)
export const getSummaryStats = () => {
  return request({
    url: '/summary/stats',
    method: 'get'
  })
}

// 获取异常趋势数据 (用于折线图)
// 参数：days (天数)
export const getIssueTrend = (days = 7) => {
  return request({
    url: '/summary/trend',
    method: 'get',
    params: { days }
  })
}

// 获取异常类型分布数据 (用于饼图)
export const getIssueDistribution = () => {
  return request({
    url: '/summary/distribution',
    method: 'get'
  })
}

// 获取最近异常记录列表 (分页、搜索、筛选)
// 参数：{ page, limit, query, status }
export const getRecentIssues = (params) => {
  return request({
    url: '/summary/recent',
    method: 'get',
    params
  })
}
