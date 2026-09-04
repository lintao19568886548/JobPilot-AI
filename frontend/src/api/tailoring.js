import { http } from './http.js'

export const getEvidenceLedger = () => http.get('/v1/evidence-ledger')
export const refreshEvidenceLedger = () => http.post('/v1/evidence-ledger:refresh')
export const listTailorRuns = () => http.get('/v1/resume-tailor-runs')
export const createTailorRun = (jobId, payload, idempotencyKey) => http.post(
  `/v1/jobs/${jobId}/resume-tailor-runs`, payload,
  { headers: { 'Idempotency-Key': idempotencyKey }, timeout: 60000 }
)
export const getTailorRun = (id) => http.get(`/v1/resume-tailor-runs/${id}`)
export const approveTailorRun = (id, payload) => http.post(`/v1/resume-tailor-runs/${id}:approve`, payload)
export const listCommunicationDrafts = () => http.get('/v1/communication-drafts')
export const createCommunicationDraft = (jobId, payload, idempotencyKey) => http.post(
  `/v1/jobs/${jobId}/communication-drafts`, payload,
  { headers: { 'Idempotency-Key': idempotencyKey }, timeout: 60000 }
)
export const getCommunicationDraft = (id) => http.get(`/v1/communication-drafts/${id}`)
export const approveCommunicationDraft = (id, version) => http.post(`/v1/communication-drafts/${id}:approve`, { version })
export const markCommunicationDraftUsed = (id, version) => http.post(`/v1/communication-drafts/${id}:mark-used`, { version })
export const getResumeVersionMetrics = (id) => http.get(`/v1/resume-versions/${id}/metrics`)
