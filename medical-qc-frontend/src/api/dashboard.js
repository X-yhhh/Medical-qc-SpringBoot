// src/api/dashboard.js
// ----------------------------------------------------------------------------------
// 首页仪表盘前端 API
// 作用：获取首页欢迎信息、统计卡片、风险预警、待办事项和趋势图数据。
// 对接后端：/api/v1/dashboard
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

/**
 * 获取首页总览数据。
 */
export const getDashboardOverview = () => {
  return request({
    url: '/dashboard/overview',
    method: 'get',
  })
}

/**
 * 获取首页质控合格率趋势。
 * @param {string} period - 周期：week / month
 */
export const getDashboardTrend = (period = 'week') => {
  return request({
    url: '/dashboard/trend',
    method: 'get',
    params: { period },
  })
}
