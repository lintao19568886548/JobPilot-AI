import { chromium } from 'playwright'
import { requireAllowedUrl, type WorkerConfig } from './config.js'
import type { PrepareRequest, PrepareResponse, PrepareStep } from './schema.js'

const blockedSelectors = [
  '[data-captcha]',
  'iframe[src*="captcha" i]',
  'input[name*="captcha" i]',
  'input[type="password"]',
  '[data-two-factor]',
  '[data-jobpilot-uncertain]'
]

export async function prepare(request: PrepareRequest, config: WorkerConfig): Promise<PrepareResponse> {
  if (!config.enabled) throw new Error('WORKER_DISABLED')
  requireAllowedUrl(request.targetUrl, config)
  const browser = await chromium.launch({ headless: config.headless })
  const steps: PrepareStep[] = []
  try {
    const page = await browser.newPage()
    await page.goto(request.targetUrl, { waitUntil: 'domcontentloaded', timeout: 15_000 })
    steps.push({ type: 'OPEN', status: 'SUCCEEDED', detail: 'Allowed local page opened' })
    for (const selector of blockedSelectors) {
      if (await page.locator(selector).first().isVisible().catch(() => false)) {
        steps.push({ type: 'BLOCKED', status: 'BLOCKED', selector, detail: 'Human verification or uncertain page detected' })
        return safeResponse(request.taskId, 'BLOCKED', steps, 'HUMAN_VERIFICATION_REQUIRED')
      }
    }
    steps.push({ type: 'CHECK', status: 'SUCCEEDED', detail: 'No CAPTCHA, password, 2FA or uncertainty marker detected' })
    for (const field of request.fields) {
      const locator = page.locator(field.selector)
      if (await locator.count() !== 1 || !await locator.isVisible()) {
        steps.push({ type: 'BLOCKED', status: 'BLOCKED', selector: field.selector, detail: 'Approved field is missing or ambiguous' })
        return safeResponse(request.taskId, 'BLOCKED', steps, 'FIELD_NOT_SAFE')
      }
      await locator.fill(field.value)
      steps.push({ type: 'FILL', status: 'SUCCEEDED', selector: field.selector, detail: 'Field filled without keyboard submit' })
    }
    const submitCount = Number(await page.locator('#submit-count').textContent().catch(() => '0'))
    if (submitCount !== 0) throw new Error('SUBMIT_BOUNDARY_VIOLATED')
    steps.push({ type: 'HANDOFF', status: 'SUCCEEDED', detail: 'User must review and perform any final submission' })
    return safeResponse(request.taskId, 'PREPARED', steps, null)
  } finally {
    await browser.close()
  }
}

function safeResponse(taskId: string, status: 'PREPARED' | 'BLOCKED', steps: PrepareStep[], blockedReason: string | null): PrepareResponse {
  return {
    schemaVersion: 'assist-prepare-response-v1', taskId, status,
    externallySubmitted: false, applicationCreated: false,
    finalConfirmationRequired: true, submitCount: 0, blockedReason, steps
  }
}
