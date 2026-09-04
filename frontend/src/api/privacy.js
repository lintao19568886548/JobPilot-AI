import { http } from './http.js'

export const exportMyData = () => http.get('/v1/privacy/export')
export const previewDataDeletion = (key = crypto.randomUUID()) => http.post('/v1/privacy/delete:preview', null, { headers: { 'Idempotency-Key': key } })
export const confirmDataDeletion = (payload, key = crypto.randomUUID()) => http.post('/v1/privacy/delete:confirm', payload, { headers: { 'Idempotency-Key': key } })
