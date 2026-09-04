import { describe, expect, it } from 'vitest'
import { loadConfig, requireAllowedUrl } from '../src/config.js'
import { allowedSelectors, prepareRequestSchema } from '../src/schema.js'
import { createHmac } from 'node:crypto'
import { verifyTaskToken } from '../src/task-token.js'

describe('automation safety contract', () => {
  it('is disabled by default and only allows local hosts', () => {
    const config = loadConfig({})
    expect(config.enabled).toBe(false)
    expect(() => requireAllowedUrl('https://jobs.example.com/apply', config)).toThrow('TARGET_NOT_ALLOWED')
    expect(requireAllowedUrl('http://127.0.0.1:8020/fixtures/application-form.html', config).hostname).toBe('127.0.0.1')
  })

  it('accepts only fixed fill selectors and requires final confirmation', () => {
    const base = {
      schemaVersion: 'assist-prepare-request-v1', taskId: '01M1TESTTASK00000000000000', taskToken: 'x'.repeat(32),
      targetUrl: 'http://127.0.0.1:8020/fixtures/application-form.html', queueApprovalId: '01M1QUEUE0000000000000000',
      policyMode: 'ASSIST_ALLOWED', finalConfirmationRequired: true,
      fields: [{ selector: '#jp-name', value: 'Candidate' }]
    }
    expect(prepareRequestSchema.safeParse(base).success).toBe(true)
    expect(prepareRequestSchema.safeParse({ ...base, fields: [{ selector: '#submit-application', value: 'x' }] }).success).toBe(false)
    expect(allowedSelectors).not.toContain('#submit-application')
  })

  it('rejects extra fields and false confirmation', () => {
    const result = prepareRequestSchema.safeParse({
      schemaVersion: 'assist-prepare-request-v1', taskId: 'task', taskToken: 'x'.repeat(32),
      targetUrl: 'http://localhost:8020/fixtures/application-form.html', queueApprovalId: 'queue',
      policyMode: 'ASSIST_ALLOWED', finalConfirmationRequired: false, fields: [], submit: true
    })
    expect(result.success).toBe(false)
  })

  it('accepts only a signed, unexpired token bound to the task', () => {
    const serviceToken = 'worker-test-token-at-least-24-characters'
    const claims = Buffer.from('01M1TESTTASK00000000000000|2000000000|01M1QUEUE0000000000000000|ASSIST_ALLOWED').toString('base64url')
    const signature = createHmac('sha256', serviceToken).update(claims).digest('base64url')
    const token = `jpt_${claims}.${signature}`
    expect(verifyTaskToken(token, '01M1TESTTASK00000000000000', '01M1QUEUE0000000000000000', 'ASSIST_ALLOWED', serviceToken, 1_900_000_000)).toBe(true)
    expect(verifyTaskToken(token, 'different-task', '01M1QUEUE0000000000000000', 'ASSIST_ALLOWED', serviceToken, 1_900_000_000)).toBe(false)
    expect(verifyTaskToken(token, '01M1TESTTASK00000000000000', 'different-queue', 'ASSIST_ALLOWED', serviceToken, 1_900_000_000)).toBe(false)
    expect(verifyTaskToken(token, '01M1TESTTASK00000000000000', '01M1QUEUE0000000000000000', 'MANUAL_ONLY', serviceToken, 1_900_000_000)).toBe(false)
    expect(verifyTaskToken(token, '01M1TESTTASK00000000000000', '01M1QUEUE0000000000000000', 'ASSIST_ALLOWED', serviceToken, 2_100_000_000)).toBe(false)
    expect(verifyTaskToken(`${token}tampered`, '01M1TESTTASK00000000000000', '01M1QUEUE0000000000000000', 'ASSIST_ALLOWED', serviceToken, 1_900_000_000)).toBe(false)
  })
})
