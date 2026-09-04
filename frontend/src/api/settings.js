import { http } from './http.js'

export const getSettings = () => http.get('/v1/settings')
export const updateAccount = (payload) => http.put('/v1/settings/account', payload)
export const updateSetting = (group, key, payload) => http.put(`/v1/settings/${group}/${key}`, payload)
