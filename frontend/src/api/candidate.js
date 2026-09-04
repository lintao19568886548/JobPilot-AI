import { http } from './http.js'

export const getProfile = () => http.get('/candidate/profile')
export const putProfile = (payload) => http.put('/candidate/profile', payload)
export const patchProfile = (payload) => http.patch('/candidate/profile', payload)
export const getCompleteness = () => http.get('/candidate/profile/completeness')

export const listEducations = () => http.get('/candidate/educations')
export const createEducation = (payload) => http.post('/candidate/educations', payload)
export const updateEducation = (id, payload) => http.put(`/candidate/educations/${id}`, payload)
export const deleteEducation = (id) => http.delete(`/candidate/educations/${id}`)

export const listExperiences = () => http.get('/candidate/experiences')
export const createExperience = (payload) => http.post('/candidate/experiences', payload)
export const updateExperience = (id, payload) => http.put(`/candidate/experiences/${id}`, payload)
export const deleteExperience = (id) => http.delete(`/candidate/experiences/${id}`)

export const listProjects = () => http.get('/candidate/projects')
export const createProject = (payload) => http.post('/candidate/projects', payload)
export const updateProject = (id, payload) => http.put(`/candidate/projects/${id}`, payload)
export const deleteProject = (id) => http.delete(`/candidate/projects/${id}`)

export const listSkills = (params = {}) => http.get('/skills', { params })
export const listCandidateSkills = () => http.get('/candidate/skills')
export const createCandidateSkill = (payload) => http.post('/candidate/skills', payload)
export const updateCandidateSkill = (id, payload) => http.put(`/candidate/skills/${id}`, payload)
export const deleteCandidateSkill = (id) => http.delete(`/candidate/skills/${id}`)

