import request from '@/utils/request'

// 提交登录表单，后端成功后会把脱敏用户快照写入 Session。
export const login = (data) => request.post('/auth/login', data)

// 主动销毁当前会话。
export const logout = () => request.post('/auth/logout')

// 从后端读取当前登录用户，用于页面初始化和路由守卫恢复会话。
export const getCurrentUser = (config = {}) => request.get('/auth/current', config)

// 提交注册表单，创建医生或管理员账号。
export const register = (data) => request.post('/auth/register', data)
