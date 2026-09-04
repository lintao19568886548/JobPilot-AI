const ACCESS_KEY = 'jobpilot_access_token'
const REFRESH_KEY = 'jobpilot_refresh_token'

export function getAccessToken() {
  return sessionStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function setTokens(accessToken, refreshToken) {
  sessionStorage.setItem(ACCESS_KEY, accessToken)
  sessionStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

