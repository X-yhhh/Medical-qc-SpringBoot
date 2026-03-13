import request from '@/utils/request'

// 获取顶部统计卡片数据。
export const getSummaryStats = () => {
  return request({
    url: '/summary/stats',
    method: 'get',
  })
}

// 获取异常趋势图数据，days 决定近 7 天或近 30 天视图。
export const getIssueTrend = (days = 7) => {
  return request({
    url: '/summary/trend',
    method: 'get',
    params: { days },
  })
}

// 获取异常类型分布，用于饼图渲染。
export const getIssueDistribution = () => {
  return request({
    url: '/summary/distribution',
    method: 'get',
  })
}

// 获取异常工单分页列表，params 内包含页码、大小、关键字和状态筛选。
export const getRecentIssues = (params) => {
  return request({
    url: '/summary/recent',
    method: 'get',
    params,
  })
}

// 列表快捷处理时只更新工单状态和备注。
export const updateIssueStatus = (issueId, data) => {
  return request({
    url: `/summary/issues/${issueId}/status`,
    method: 'patch',
    data,
  })
}

// 获取工单详情及其原始记录、CAPA 和处理日志。
export const getIssueDetail = (issueId) => {
  return request({
    url: `/summary/issues/${issueId}`,
    method: 'get',
  })
}

// 获取可分派处理人列表，用于详情弹窗中的指派选择器。
export const getAssignableUsers = () => {
  return request({
    url: '/summary/operators',
    method: 'get',
  })
}

// 更新工单工作流字段，包括状态、指派人和 CAPA 整改信息。
export const updateIssueWorkflow = (issueId, data) => {
  return request({
    url: `/summary/issues/${issueId}/workflow`,
    method: 'patch',
    data,
  })
}
