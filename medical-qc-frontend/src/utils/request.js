// src/utils/request.js
/**
 * @file src/utils/request.js
 * @description Axios 请求封装模块
 * 主要功能：
 * 1. 创建全局唯一的 Axios 实例，配置 BaseURL 和超时时间。
 * 2. 开启 withCredentials，支持 Session Cookie。
 * 3. 响应拦截器：统一处理 API 响应错误，特别是 401 未授权自动登出。
 */

import axios from 'axios'

let router = null

// 注入路由实例 (避免循环引用)
export const setRouter = (r) => {
  router = r
}

// 创建 Axios 实例
const instance = axios.create({
  // BaseURL 配置：优先使用环境变量，否则默认为相对路径 /api/v1 (走 Vite 代理)
  baseURL: (import.meta.env.VITE_API_BASE_URL || '') + '/api/v1',
  timeout: 30000, // 超时时间：30秒 (AI 分析可能较慢)
  withCredentials: true // 允许携带 Cookie (Session ID)
})

// ----------------------------------------------------------------------------------
// 请求拦截器 (Request Interceptor)
// ----------------------------------------------------------------------------------
instance.interceptors.request.use((config) => {
  // Session 模式下无需手动添加 Authorization 头
  // 浏览器会自动管理 Cookie
  return config
})

// ----------------------------------------------------------------------------------
// 响应拦截器 (Response Interceptor)
// ----------------------------------------------------------------------------------
instance.interceptors.response.use(
  // 成功：直接返回 data 部分，简化调用方代码
  (response) => response.data,

  // 失败：统一错误处理
  (error) => {
    // 处理 401 Unauthorized (Session 过期或未登录)
    if (error.response?.status === 401) {
      // 1. 清除本地存储的用户信息 (Session Cookie 由浏览器清除或覆盖)
      sessionStorage.removeItem('user_info')

      // 2. 跳转回登录页
      if (router) {
        router.push('/login')
      } else {
        window.location.href = '/login'
      }
    }
    // 继续抛出错误，供调用方具体处理
    return Promise.reject(error)
  },
)

export default instance
