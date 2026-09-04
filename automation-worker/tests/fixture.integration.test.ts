import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { createApp } from '../src/app.js'
import { prepare } from '../src/prepare.js'
import type { WorkerConfig } from '../src/config.js'
import type { PrepareRequest } from '../src/schema.js'

const serviceToken = 'fixture-worker-token-at-least-24-characters'
const config: WorkerConfig = {
  enabled: true, port: 0, serviceToken, allowedHosts: new Set(['127.0.0.1']), headless: true
}
let server: Server
let baseUrl: string

beforeAll(async () => {
  server = createServer(createApp(config))
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})

afterAll(async () => { await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve())) })

function request(path: string): PrepareRequest {
  return {
    schemaVersion: 'assist-prepare-request-v1', taskId: '01M1TESTTASK00000000000000', taskToken: 'x'.repeat(32),
    targetUrl: `${baseUrl}${path}`, queueApprovalId: '01M1QUEUE0000000000000000', policyMode: 'ASSIST_ALLOWED',
    finalConfirmationRequired: true,
    fields: [
      { selector: '#jp-name', value: 'Candidate' },
      { selector: '#jp-email', value: 'candidate@example.test' },
      { selector: '#jp-summary', value: 'Backend engineer' }
    ]
  }
}

describe('local Playwright Assist fixture', () => {
  it('fills approved fields and stops before external submission', async () => {
    const result = await prepare(request('/fixtures/application-form.html'), config)
    expect(result.status).toBe('PREPARED')
    expect(result.steps.filter((step) => step.type === 'FILL')).toHaveLength(3)
    expect(result.steps.at(-1)?.type).toBe('HANDOFF')
    expect(result.externallySubmitted).toBe(false)
    expect(result.applicationCreated).toBe(false)
    expect(result.finalConfirmationRequired).toBe(true)
    expect(result.submitCount).toBe(0)
  }, 30_000)

  it('stops on a CAPTCHA marker without filling or submitting', async () => {
    const result = await prepare(request('/fixtures/captcha-form.html'), config)
    expect(result.status).toBe('BLOCKED')
    expect(result.blockedReason).toBe('HUMAN_VERIFICATION_REQUIRED')
    expect(result.steps.some((step) => step.type === 'FILL')).toBe(false)
    expect(result.submitCount).toBe(0)
    expect(result.externallySubmitted).toBe(false)
  }, 30_000)
})
