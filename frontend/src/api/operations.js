import { http } from './http.js'

export const getOperationsOverview = () => http.get('/v1/operations/overview')
export const getAiUsage = () => http.get('/v1/operations/ai-usage')
export const getAiBudget = () => http.get('/v1/operations/ai-budget')
export const updateAiBudget = (payload) => http.put('/v1/operations/ai-budget', payload)
export const listOperationalRuns = (type) => http.get('/v1/operations/runs', { params: type ? { type } : {} })
export const getOperationalRun = (id) => http.get(`/v1/operations/runs/${id}`)
export const createOperationalRun = (payload, idempotencyKey) => http.post('/v1/operations/runs', payload, { headers: { 'Idempotency-Key': idempotencyKey } })
