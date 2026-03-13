import request from '@/utils/request'

export const getSummaryStats = () => {
  return request({
    url: '/summary/stats',
    method: 'get',
  })
}

export const getIssueTrend = (days = 7) => {
  return request({
    url: '/summary/trend',
    method: 'get',
    params: { days },
  })
}

export const getIssueDistribution = () => {
  return request({
    url: '/summary/distribution',
    method: 'get',
  })
}

export const getRecentIssues = (params) => {
  return request({
    url: '/summary/recent',
    method: 'get',
    params,
  })
}

export const updateIssueStatus = (issueId, data) => {
  return request({
    url: `/summary/issues/${issueId}/status`,
    method: 'patch',
    data,
  })
}

export const getIssueDetail = (issueId) => {
  return request({
    url: `/summary/issues/${issueId}`,
    method: 'get',
  })
}

export const getAssignableUsers = () => {
  return request({
    url: '/summary/operators',
    method: 'get',
  })
}

export const updateIssueWorkflow = (issueId, data) => {
  return request({
    url: `/summary/issues/${issueId}/workflow`,
    method: 'patch',
    data,
  })
}
