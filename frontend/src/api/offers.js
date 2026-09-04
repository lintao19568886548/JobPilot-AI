import { http } from './http.js'

export const listOffers = (params = {}) => http.get('/v1/offers', { params })
export const getOffer = (id) => http.get(`/v1/offers/${id}`)
export const createOffer = (payload) => http.post('/v1/offers', payload)
export const updateOffer = (id, payload) => http.put(`/v1/offers/${id}`, payload)
export const updateOfferStatus = (id, status, version) => http.post(`/v1/offers/${id}/status`, { status, version })
export const deleteOffer = (id) => http.delete(`/v1/offers/${id}`)
export const getOfferDashboard = () => http.get('/v1/offers/dashboard')

export const listOfferDeadlines = (status) => http.get('/v1/offer-deadlines', { params: status ? { status } : {} })
export const createOfferDeadline = (payload) => http.post('/v1/offer-deadlines', payload)
export const completeOfferDeadline = (id, version) => http.post(`/v1/offer-deadlines/${id}/done`, { version })
export const cancelOfferDeadline = (id, version) => http.post(`/v1/offer-deadlines/${id}/cancel`, { version })

export const listOfferComparisons = () => http.get('/v1/offer-comparisons')
export const getOfferComparison = (id) => http.get(`/v1/offer-comparisons/${id}`)
export const createOfferComparison = (payload, key = crypto.randomUUID()) => http.post('/v1/offer-comparisons', payload, { headers: { 'Idempotency-Key': key } })
