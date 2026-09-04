import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

type Manifest = { manifest_version: number; minimum_chrome_version: string; permissions: string[]; host_permissions: string[]; background: { service_worker: string }; action: { default_popup: string } }

describe('Manifest V3 least privilege', () => {
  const manifest = JSON.parse(readFileSync('manifest.json', 'utf8')) as Manifest

  it('uses a local service worker and the exact approved permissions', () => {
    expect(manifest.manifest_version).toBe(3)
    expect(Number(manifest.minimum_chrome_version)).toBeGreaterThanOrEqual(116)
    expect(manifest.background.service_worker).toBe('service-worker.js')
    expect(manifest.action.default_popup).toBe('sidepanel.html')
    expect(manifest.permissions.sort()).toEqual(['activeTab', 'scripting', 'sidePanel', 'storage'].sort())
    expect(manifest.host_permissions).toEqual(['http://127.0.0.1:8088/*'])
  })

  it('never requests broad or sensitive browser privileges', () => {
    const serialized = JSON.stringify(manifest)
    for (const forbidden of ['<all_urls>', 'cookies', 'history', 'webRequest', 'debugger', 'nativeMessaging']) {
      expect(serialized).not.toContain(forbidden)
    }
  })
})
