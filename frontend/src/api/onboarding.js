import { http } from './http.js'

export const getOnboardingOverview = () => http.get('/v1/onboarding/overview')
export const getDataQuality = () => http.get('/v1/onboarding/data-quality')
