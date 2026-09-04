import { http } from './http.js'

export const getLearningDashboard = () => http.get('/v1/learning/dashboard')
export const rebuildFeedback = (payload, key = crypto.randomUUID()) => http.post('/v1/learning/feedback:rebuild', payload, { headers: { 'Idempotency-Key': key } })
export const trainModel = (payload, key = crypto.randomUUID()) => http.post('/v1/learning/models:train', payload, { headers: { 'Idempotency-Key': key } })
export const listModels = () => http.get('/v1/learning/models')
export const shadowModel = (id, key = crypto.randomUUID()) => http.post(`/v1/learning/models/${id}:shadow`, null, { headers: { 'Idempotency-Key': key } })
export const activateModel = (model) => http.post(`/v1/learning/models/${model.id}:activate`, { version: model.version })
export const rollbackModel = (model) => http.post(`/v1/learning/models/${model.id}:rollback`, { version: model.version })
