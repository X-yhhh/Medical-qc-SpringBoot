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
import { clearAuthState } from '@/utils/auth'

// 通过延迟注入拿到路由实例，避免 request.js 与 router/index.js 形成循环依赖。
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
    // 某些内部请求会自己处理未登录态，这里允许按请求级别跳过默认跳转。
    const skipAuthRedirect = Boolean(error.config?.skipAuthRedirect)

    // 处理 401 Unauthorized (Session 过期或未登录)
    if (error.response?.status === 401) {
      // 一旦后端判定会话失效，本地缓存必须立即清空，避免菜单和用户信息残留。
      clearAuthState()

      if (!skipAuthRedirect) {
        // 根据后端 detail 细分“会话过期”和“权限/状态变化”两类登录页提示。
        const unauthorizedDetail = error.response?.data?.detail || ''
        const loginMode = unauthorizedDetail.includes('权限已变更') || unauthorizedDetail.includes('账号状态已变更')
          ? 'permission-updated'
          : 'expired'

        if (router) {
          // 优先走路由实例跳转，保持 SPA 状态切换一致。
          router.push(`/login?mode=${loginMode}`)
        } else {
          // 在应用尚未完成路由注入时，退化到浏览器原生跳转。
          window.location.href = `/login?mode=${loginMode}`
        }
      }
    }

    if (error.response?.status === 403) {
      // 已登录但无权限的请求统一跳转到无权限页。
      if (router) {
        router.push('/forbidden')
      } else {
        window.location.href = '/forbidden'
      }
    }
    // 继续抛出错误，供调用方具体处理
    return Promise.reject(error)
  },
)

export default instance

