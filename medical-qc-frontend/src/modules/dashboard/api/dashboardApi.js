import request from '@/utils/request'

export const getDashboardOverview = () => {
  return request({
    url: '/dashboard/overview',
    method: 'get',
  })
}

export const getDashboardTrend = (period = 'week') => {
  return request({
    url: '/dashboard/trend',
    method: 'get',
    params: { period },
  })
}
