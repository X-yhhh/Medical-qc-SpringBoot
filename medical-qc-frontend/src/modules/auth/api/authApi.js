import request from '@/utils/request'

export const login = (data) => request.post('/auth/login', data)

export const logout = () => request.post('/auth/logout')

export const getCurrentUser = (config = {}) => request.get('/auth/current', config)

export const register = (data) => request.post('/auth/register', data)
