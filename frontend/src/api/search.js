import { http } from './http.js'

export const globalSearch = (q, types = 'JOB,COMPANY,SKILL,STATUS', limit = 8) => http.get('/search', {
  params: { q, types, limit }
})
