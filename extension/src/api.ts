import type { ExtensionTokens } from './types.js'

const BASE_URL = 'http://127.0.0.1:8088/api/v1'

export class ExtensionAuthenticationError extends Error {
  constructor() {
    super('设备授权已失效，请重新配对。')
    this.name = 'ExtensionAuthenticationError'
  }
}

export async function pair(pairingCode: string): Promise<ExtensionTokens> {
  const manifest = chrome.runtime.getManifest()
  const result = await request<ExtensionTokens>('/extension/pairings', {
    method: 'POST', auth: false, body: {
      pairingCode, deviceName: '浏览器扩展', browserName: 'Chrome 浏览器',
      extensionId: chrome.runtime.id, extensionVersion: manifest.version
    }
  })
  await chrome.storage.session.set({ extensionTokens: result })
  return result
}

export async function api<T = unknown>(path: string, options: { method?: string; body?: unknown; key?: string } = {}): Promise<T> {
  return request<T>(path, { ...options, auth: true })
}

async function request<T>(path: string, options: { method?: string; body?: unknown; key?: string; auth: boolean }, retry = true): Promise<T> {
  const stored = await chrome.storage.session.get('extensionTokens')
  const tokens = stored.extensionTokens as ExtensionTokens | undefined
  const headers: Record<string, string> = { 'Content-Type': 'application/json', 'X-Trace-Id': crypto.randomUUID() }
  if (options.auth && tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`
  if (options.key) headers['Idempotency-Key'] = options.key
  const response = await fetch(`${BASE_URL}${path}`, { method: options.method || 'GET', headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body) })
  if (response.status === 401 && options.auth && retry && tokens?.refreshToken) {
    try {
      const refreshed = await request<ExtensionTokens>('/extension/tokens/refresh', { method: 'POST', auth: false,
        body: { refreshToken: tokens.refreshToken } }, false)
      await chrome.storage.session.set({ extensionTokens: refreshed })
      return request(path, options, false)
    } catch {
      await clearExtensionSession()
      throw new ExtensionAuthenticationError()
    }
  }
  const payload = await response.json()
  if (response.status === 401 && options.auth) {
    await clearExtensionSession()
    throw new ExtensionAuthenticationError()
  }
  if (!response.ok || payload.code !== 0) throw new Error(payload.message || `HTTP ${response.status}`)
  return payload.data as T
}

async function clearExtensionSession() {
  await chrome.storage.session.remove(['extensionTokens', 'activeExtractionTabId'])
}
