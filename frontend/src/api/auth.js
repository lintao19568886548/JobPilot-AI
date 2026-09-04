import { http } from './http.js'

export const login = (payload) => http.post('/auth/login', payload)
export const getMe = () => http.get('/auth/me')
export const logout = (refreshToken) => http.post('/auth/logout', { refreshToken })
export const changePassword = (payload) => http.post('/v1/auth/password', payload)
export const getSessions = () => http.get('/v1/auth/sessions')
export const revokeSession = (sessionId) => http.delete(`/v1/auth/sessions/${sessionId}`)
