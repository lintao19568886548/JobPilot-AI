import { http } from './http.js'

export const listJobs = (params) => http.get('/jobs', { params })
export const getJob = (id) => http.get(`/jobs/${id}`)
export const createJob = (payload) => http.post('/jobs', payload)
export const updateJob = (id, payload) => http.patch(`/jobs/${id}`, payload)
export const deleteJob = (id) => http.delete(`/jobs/${id}`)
export const parseJob = (id) => http.post(`/jobs/${id}/parse-runs`)
export const ignoreJob = (id) => http.post(`/jobs/${id}:ignore`)
export const restoreJob = (id) => http.post(`/jobs/${id}:restore`)
export const importJobUrl = (payload) => http.post('/job-imports/url', payload)
export const importJobFile = (file, idempotencyKey) => {
  const body = new FormData()
  body.append('file', file)
  return http.post('/job-imports/files', body, {
    headers: { 'Content-Type': 'multipart/form-data', 'Idempotency-Key': idempotencyKey },
    timeout: 60000
  })
}
export const getImportTask = (id) => http.get(`/job-imports/${id}`)
export const getImportErrors = (id) => http.get(`/job-imports/${id}/errors`)
export const startMatch = (jobId, payload, idempotencyKey) => http.post(`/jobs/${jobId}/match-runs`, payload, {
  headers: { 'Idempotency-Key': idempotencyKey },
  timeout: 30000
})
export const getMatchRun = (id) => http.get(`/match-runs/${id}`)
export const retryMatchRun = (id) => http.post(`/match-runs/${id}:retry`)
export const listJobMatches = (jobId) => http.get(`/jobs/${jobId}/matches`)
export const getJobMatch = (id) => http.get(`/job-matches/${id}`)
