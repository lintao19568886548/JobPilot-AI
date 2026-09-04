import { defineStore } from 'pinia'
import { getSettings, updateAccount as updateAccountRequest, updateSetting as updateSettingRequest } from '../api/settings.js'

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    overview: null,
    loading: false
  }),
  getters: {
    account: (state) => state.overview?.account || null,
    defaultLandingPage: (state) => preference(state, 'workspace', 'defaultLandingPage', '/dashboard'),
    compactMode: (state) => preference(state, 'workspace', 'compactMode', false),
    showDashboardBanner: (state) => preference(state, 'onboarding', 'showDashboardBanner', true)
  },
  actions: {
    async load() {
      this.loading = true
      try {
        const { data } = await getSettings()
        this.overview = data.data
        return this.overview
      } finally {
        this.loading = false
      }
    },
    async updateAccount(payload) {
      const { data } = await updateAccountRequest(payload)
      this.overview.account = data.data
      return data.data
    },
    async updatePreference(group, key, value) {
      const current = this.overview?.preferences?.find((item) => item.group === group && item.key === key)
      const { data } = await updateSettingRequest(group, key, { value, version: current?.version ?? 0 })
      const index = this.overview.preferences.findIndex((item) => item.group === group && item.key === key)
      if (index >= 0) this.overview.preferences[index] = data.data
      else this.overview.preferences.push(data.data)
      return data.data
    }
  }
})

function preference(state, group, key, fallback) {
  return state.overview?.preferences?.find((item) => item.group === group && item.key === key)?.value ?? fallback
}
