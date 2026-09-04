import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ExtensionAuthenticationError } from '../src/api.js'

const session = {
  get: vi.fn(),
  set: vi.fn(),
  remove: vi.fn()
}

describe('extension API authentication lifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('chrome', { storage: { session } })
    session.get.mockResolvedValue({
      extensionTokens: { accessToken: 'jpe_access', refreshToken: 'jpr_refresh' }
    })
  })

  it('clears session credentials when a revoked device cannot refresh', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 40101, message: 'Unauthorized', data: null }), {
        status: 401, headers: { 'Content-Type': 'application/json' }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 40101, message: 'Unauthorized', data: null }), {
        status: 401, headers: { 'Content-Type': 'application/json' }
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api('/extension/jobs/job-id/workspace')).rejects.toBeInstanceOf(ExtensionAuthenticationError)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(session.remove).toHaveBeenCalledWith(['extensionTokens', 'activeExtractionTabId'])
    expect(session.set).not.toHaveBeenCalled()
  })
})
