import request from '@/utils/request'

// 获取首页看板总览数据。
export const getDashboardOverview = () => {
  return request({
    url: '/dashboard/overview',
    method: 'get',
  })
}

// 获取首页趋势图数据，period 决定周视图或月视图。
export const getDashboardTrend = (period = 'week') => {
  return request({
    url: '/dashboard/trend',
    method: 'get',
    params: { period },
  })
}
