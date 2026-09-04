import { http } from './http.js'

const root = '/v1/automation-center'
export const getAutomationDashboard = () => http.get(`${root}/dashboard`)
export const listRules = () => http.get(`${root}/rules`)
export const createRule = (payload) => http.post(`${root}/rules`, payload)
export const updateRule = (id, payload) => http.put(`${root}/rules/${id}`, payload)
export const runRule = (id, payload = { defer: false }, key = crypto.randomUUID()) => http.post(`${root}/rules/${id}:run`, payload, { headers: { 'Idempotency-Key': key } })
export const listTasks = () => http.get(`${root}/tasks`)
export const cancelTask = (task) => http.post(`${root}/tasks/${task.id}:cancel`, { version: task.version })
export const retryTask = (task) => http.post(`${root}/tasks/${task.id}:retry`, { version: task.version })
export const listSuggestions = () => http.get(`${root}/suggestions`)
export const decideSuggestion = (suggestion, decision) => http.post(`${root}/suggestions/${suggestion.id}:${decision}`, { version: suggestion.version })
export const listNotifications = () => http.get(`${root}/notifications`)
export const readNotification = (notification) => http.post(`${root}/notifications/${notification.id}:read`, { version: notification.version })
export const listAuthorizations = () => http.get(`${root}/authorizations`)
export const grantAuthorization = (payload) => http.post(`${root}/authorizations`, payload)
export const revokeAuthorization = (id) => http.delete(`${root}/authorizations/${id}`)
export const getPolicyDecision = (platform, scope) => http.get(`${root}/policy-decision`, { params: { platform, scope } })
