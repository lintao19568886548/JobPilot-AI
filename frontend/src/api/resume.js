import { http } from './http.js'

export const listResumes = () => http.get('/resumes')
export const createResume = (payload) => http.post('/resumes', payload)
export const getResume = (id) => http.get(`/resumes/${id}`)
export const updateResume = (id, payload) => http.put(`/resumes/${id}`, payload)
export const deleteResume = (id) => http.delete(`/resumes/${id}`)
export const createResumeVersion = (id, payload) => http.post(`/resumes/${id}/versions`, payload)
export const listResumeVersions = (id) => http.get(`/resumes/${id}/versions`)
export const getResumeVersion = (id) => http.get(`/resume-versions/${id}`)
export const setDefaultResume = (id) => http.post(`/resumes/${id}/set-default`)
export const setMasterResume = (id) => http.post(`/resumes/${id}/set-master`)

