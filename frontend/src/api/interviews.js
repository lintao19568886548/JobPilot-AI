import { http } from './http.js'

export const listInterviews = (params = {}) => http.get('/v1/interviews', { params })
export const getInterview = (id) => http.get(`/v1/interviews/${id}`)
export const createInterview = (payload) => http.post('/v1/interviews', payload)
export const updateInterview = (id, payload) => http.put(`/v1/interviews/${id}`, payload)
export const deleteInterview = (id) => http.delete(`/v1/interviews/${id}`)

export const createInterviewRound = (interviewId, payload) => http.post(`/v1/interviews/${interviewId}/rounds`, payload)
export const updateInterviewRound = (id, payload) => http.put(`/v1/interview-rounds/${id}`, payload)
export const deleteInterviewRound = (id) => http.delete(`/v1/interview-rounds/${id}`)

export const predictQuestions = (roundId, key = crypto.randomUUID()) => http.post(
  `/v1/interview-rounds/${roundId}/questions:predict`, null, { headers: { 'Idempotency-Key': key } }
)
export const createInterviewQuestion = (roundId, payload) => http.post(`/v1/interview-rounds/${roundId}/questions`, payload)
export const updateInterviewQuestion = (id, payload) => http.put(`/v1/interview-questions/${id}`, payload)
export const addAnswerNote = (id, payload) => http.post(`/v1/interview-questions/${id}/answer-notes`, payload)

export const generateInterviewReview = (interviewId, key = crypto.randomUUID()) => http.post(
  `/v1/interviews/${interviewId}/reviews:generate`, null, { headers: { 'Idempotency-Key': key } }
)
export const listInterviewReviews = (interviewId) => http.get(`/v1/interviews/${interviewId}/reviews`)
export const confirmInterviewReview = (id, version) => http.post(`/v1/interview-reviews/${id}:confirm`, { version })

export const listKnowledgeGaps = (status) => http.get('/v1/knowledge-gaps', { params: status ? { status } : {} })
export const activateKnowledgeGap = (id, version) => http.post(`/v1/knowledge-gaps/${id}:activate`, { version })
export const resolveKnowledgeGap = (id, version) => http.post(`/v1/knowledge-gaps/${id}:resolve`, { version })
export const dismissKnowledgeGap = (id, version) => http.post(`/v1/knowledge-gaps/${id}:dismiss`, { version })

export const listInterviewReminders = (status) => http.get('/v1/interview-reminders', { params: status ? { status } : {} })
export const createInterviewReminder = (payload) => http.post('/v1/interview-reminders', payload)
export const updateInterviewReminder = (id, payload) => http.put(`/v1/interview-reminders/${id}`, payload)
export const completeInterviewReminder = (id, version) => http.post(`/v1/interview-reminders/${id}:done`, { version })
export const cancelInterviewReminder = (id, version) => http.post(`/v1/interview-reminders/${id}:cancel`, { version })
export const deleteInterviewReminder = (id) => http.delete(`/v1/interview-reminders/${id}`)

export const getInterviewDashboard = () => http.get('/v1/interviews/dashboard')
