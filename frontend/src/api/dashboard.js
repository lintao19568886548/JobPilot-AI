import { http } from './http.js'

export const getDashboard = () => http.get('/dashboard')

