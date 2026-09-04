import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { chromium } from '../extension/node_modules/playwright/index.mjs'

const root = new URL('../', import.meta.url)
const envText = await readFile(new URL('.env', root), 'utf8')

function envValue(name) {
  const line = envText.split(/\r?\n/).filter((item) => item.trim().startsWith(`${name}=`)).at(-1)
  if (!line) throw new Error(`Missing local environment value: ${name}`)
  const raw = line.slice(line.indexOf('=') + 1).trim()
  return raw.replace(/^(['"])(.*)\1$/, '$2')
}

const password = `P8!${createHash('sha256').update(`phase8-smoke|${envValue('JWT_SECRET')}`).digest('hex').slice(0, 30).toUpperCase()}z`
const errors = []
const warnings = []
const browser = await chromium.launch({ channel: 'msedge', headless: true })

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.on('pageerror', (error) => errors.push(`pageerror:${error.message}`))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console:${message.text()} @ ${message.location().url || 'unknown'}`)
    if (message.type() === 'warning') warnings.push(message.text())
  })

  await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
  await page.locator('input[autocomplete="username"]').fill('phase8_smoke')
  await page.locator('input[autocomplete="current-password"]').fill(password)
  await page.getByRole('button', { name: '进入 JobPilot' }).click()
  await page.waitForURL('**/dashboard')
  await page.getByRole('heading', { name: '近期面试与提醒' }).waitFor()
  const dashboardText = await page.locator('main.main-area').innerText()
  for (const expected of ['近期面试', '待办提醒', '待确认复盘', '活动知识缺口', 'EXTERNAL_MEETING_ACTIONS', 'MANUAL_ONLY']) {
    if (!dashboardText.includes(expected)) throw new Error(`Dashboard is missing: ${expected}`)
  }

  await page.getByRole('link', { name: /Interview Center/ }).first().click()
  await page.waitForURL('**/interviews')
  await page.getByRole('heading', { name: 'Interview Center' }).waitFor()
  await page.getByText('ROUND 1', { exact: false }).first().waitFor()
  const detailText = await page.locator('main.main-area').innerText()
  for (const expected of ['预测题', '实际题', 'Answer Note v2', 'ROUND 1', 'ROUND 2']) {
    if (!detailText.includes(expected)) throw new Error(`Interview detail is missing: ${expected}`)
  }

  await page.getByRole('button', { name: /复盘版本/ }).click()
  const reviewText = await page.locator('main.main-area').innerText()
  for (const expected of ['Review v2', 'Review v1', '草稿', '已确认']) {
    if (!reviewText.includes(expected)) throw new Error(`Review history is missing: ${expected}`)
  }

  await page.getByRole('button', { name: /Knowledge Gaps/ }).click()
  const gapText = await page.locator('main.main-area').innerText()
  for (const expected of ['待确认', '补习中', '已驳回']) {
    if (!gapText.includes(expected)) throw new Error(`Knowledge Gap state is missing: ${expected}`)
  }

  await page.getByRole('button', { name: /提醒 3/ }).click()
  const reminderText = await page.locator('main.main-area').innerText()
  for (const expected of ['待办', '已完成', '已取消']) {
    if (!reminderText.includes(expected)) throw new Error(`Reminder state is missing: ${expected}`)
  }

  if (errors.length) throw new Error(`Browser emitted ${errors.length} error(s): ${errors.join(' | ')}`)
  process.stdout.write(JSON.stringify({
    status: 'PASS',
    browser: 'Microsoft Edge',
    login: 'PASS',
    dashboard: 'PASS',
    interviewDetail: 'PASS',
    reviewHistory: 'PASS',
    knowledgeGaps: 'PASS',
    reminders: 'PASS',
    consoleErrors: 0,
    consoleWarnings: warnings.length
  }))
} finally {
  await browser.close()
}
