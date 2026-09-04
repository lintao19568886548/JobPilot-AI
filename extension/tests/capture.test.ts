import { describe, expect, it } from 'vitest'
import { buildCapturePayload, validateCapturePayload } from '../src/capture.js'
import type { VisibleJobSnapshot } from '../src/types.js'

const snapshot: VisibleJobSnapshot = {
  adapterId: 'FIXTURE_V1', adapterVersion: 'fixture_v1', status: 'READY', platform: 'DEMO_FIXTURE',
  pageUrl: 'http://127.0.0.1:8020/fixtures/job-page.html', capturedAt: '2026-09-02T05:00:00.000',
  visibleFields: { jobTitle: 'Java', companyName: 'Example', descriptionText: 'Build services' },
  contentHash: 'a'.repeat(64), warnings: []
}

describe('capture payload allowlist', () => {
  it('contains exactly the backend-approved top-level keys', () => {
    const payload = buildCapturePayload(snapshot, snapshot.visibleFields)
    expect(Object.keys(payload).sort()).toEqual([
      'adapterVersion', 'capturedAt', 'contentHash', 'pageUrl', 'platform', 'userInitiated', 'visibleFields'
    ])
    expect(validateCapturePayload(payload)).toBeUndefined()
    expect(JSON.stringify(payload)).not.toMatch(/cookie|authorization|localStorage|sessionStorage|password/i)
  })

  it('rejects an incomplete visible snapshot', () => {
    const payload = buildCapturePayload(snapshot, { ...snapshot.visibleFields, companyName: '' })
    expect(() => validateCapturePayload(payload)).toThrow('请补全')
  })
})
