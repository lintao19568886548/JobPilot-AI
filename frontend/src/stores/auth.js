import { defineStore } from 'pinia'
import { getMe, login as loginRequest, logout as logoutRequest } from '../api/auth.js'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../utils/authTokens.js'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    loading: false
  }),
  getters: {
    authenticated: () => Boolean(getAccessToken())
  },
  actions: {
    async login(credentials) {
      this.loading = true
      try {
        const { data } = await loginRequest(credentials)
        setTokens(data.data.accessToken, data.data.refreshToken)
        this.user = data.data.user
      } finally {
        this.loading = false
      }
    },
    async restore() {
      if (!getAccessToken()) return false
      try {
        const { data } = await getMe()
        this.user = data.data
        return true
      } catch {
        clearTokens()
        this.user = null
        return false
      }
    },
    async logout() {
      const refreshToken = getRefreshToken()
      try {
        if (refreshToken) await logoutRequest(refreshToken)
      } finally {
        clearTokens()
        this.user = null
      }
    },
    clearSession() {
      clearTokens()
      this.user = null
    }
  }
})
