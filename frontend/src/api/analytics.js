import { http } from './http.js'

export const getAnalyticsDashboard = () => http.get('/analytics/dashboard')
export const getLevelDistribution = () => http.get('/analytics/levels')
export const getSourceDistribution = () => http.get('/analytics/sources')
export const getRecommendationFunnel = () => http.get('/analytics/funnel')
export const rebuildAnalytics = (payload) => http.post('/analytics:rebuild', payload)
export const getCompleteAnalytics = (params = {}) => http.get('/v1/analytics/complete', { params })
export const rebuildCompleteAnalytics = (payload, key = crypto.randomUUID()) => http.post('/v1/analytics/complete:rebuild', payload, { headers: { 'Idempotency-Key': key } })
export const listAnalyticsSnapshots = () => http.get('/v1/analytics/snapshots')
