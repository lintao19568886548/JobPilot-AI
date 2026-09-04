import { http } from './http.js'

export const getRecommendationCapabilities = () => http.get('/recommendations/capabilities')
export const listRecommendations = (params) => http.get('/recommendations', { params })
export const getRecommendation = (id) => http.get(`/recommendations/${id}`)
export const getRecommendationEvents = (id) => http.get(`/recommendations/${id}/events`)
export const favoriteRecommendation = (id, version) => http.post(`/recommendations/${id}:favorite`, { version })
export const unfavoriteRecommendation = (id, version) => http.post(`/recommendations/${id}:unfavorite`, { version })
export const ignoreRecommendation = (id, version, reason) => http.post(`/recommendations/${id}:ignore`, { version, reason })
export const restoreRecommendation = (id, version) => http.post(`/recommendations/${id}:restore`, { version })
export const createRecommendationRefresh = (payload, idempotencyKey) => http.post('/recommendation-refresh-runs', payload, {
  headers: { 'Idempotency-Key': idempotencyKey }, timeout: 30000
})
export const getRecommendationRefresh = (id) => http.get(`/recommendation-refresh-runs/${id}`)
export const retryRecommendationRefresh = (id) => http.post(`/recommendation-refresh-runs/${id}:retry-failed`)
