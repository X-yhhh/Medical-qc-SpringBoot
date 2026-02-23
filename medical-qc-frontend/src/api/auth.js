// src/api/auth.js
// ----------------------------------------------------------------------------------
// 认证模块前端 API (Auth API)
// 作用：提供前端登录、注册的接口调用方法。
// 对接后端：/api/v1/auth
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

// 用户登录
// 参数：{ username, password }
export const login = (data) => {
  return request.post('/auth/login', data)
}

// 用户退出登录
export const logout = () => {
  return request.post('/auth/logout')
}

// 获取当前用户信息 (检查 Session 有效性)
export const getCurrentUser = () => {
  return request.get('/auth/current')
}

// 用户注册
// 参数：{ username, password, email, full_name, ... }
export const register = (data) => {
  return request.post('/auth/register', data)
}
