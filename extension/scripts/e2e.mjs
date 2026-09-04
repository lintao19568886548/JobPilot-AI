import assert from 'node:assert/strict'
import process from 'node:process'
import { cp, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { chromium } from 'playwright'
import { URL } from 'node:url'

const sandboxPath = await mkdtemp(join(tmpdir(), 'jp-ext-e2e-'))
const extensionPath = join(sandboxPath, 'extension').replaceAll('\\', '/')
const profilePath = join(sandboxPath, 'profile').replaceAll('\\', '/')
await cp(resolve('dist'), extensionPath, { recursive: true })
// The production extension intentionally relies on activeTab. Playwright cannot
// synthesize a trusted toolbar-action click, so the isolated test copy receives
// access only to the local fixture origin to exercise the real injection path.
const testManifestPath = join(extensionPath, 'manifest.json')
const testManifest = JSON.parse(await readFile(testManifestPath, 'utf8'))
testManifest.host_permissions.push('http://127.0.0.1:8020/*')
await writeFile(testManifestPath, `${JSON.stringify(testManifest, null, 2)}\n`)
let context
try {
  context = await chromium.launchPersistentContext(profilePath, {
    // Reuse the machine's installed Edge. The bundled Playwright Chromium can
    // be unavailable on locked-down Windows hosts because its side-by-side
    // runtime cannot start, while Edge is already a supported prerequisite.
    channel: 'msedge', headless: false,
    args: [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`]
  })
  let [serviceWorker] = context.serviceWorkers()
  if (!serviceWorker) serviceWorker = await context.waitForEvent('serviceworker', { timeout: 15_000 })
  const extensionId = new URL(serviceWorker.url()).host
  assert.match(extensionId, /^[a-p]{32}$/)

  const fixture = await context.newPage()
  await fixture.goto('http://127.0.0.1:8020/fixtures/job-page.html', { waitUntil: 'domcontentloaded' })
  await fixture.bringToFront()
  const snapshot = await serviceWorker.evaluate(async () => {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    if (!tab?.id) throw new Error('ACTIVE_TAB_MISSING')
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['content-script.js'] })
    return chrome.tabs.sendMessage(tab.id, { type: 'JOBPILOT_EXTRACT_VISIBLE' })
  })
  assert.equal(snapshot.adapterId, 'FIXTURE_V1')
  assert.equal(snapshot.status, 'READY')
  assert.equal(snapshot.visibleFields.jobTitle, 'Senior Java Backend Engineer')
  assert.ok(!JSON.stringify(snapshot).includes('never-read-this-password'))
  assert.ok(!JSON.stringify(snapshot).includes('session-secret'))

  const sidepanel = await context.newPage()
  const pageErrors = []
  sidepanel.on('pageerror', (error) => pageErrors.push(error.message))
  await sidepanel.goto(`chrome-extension://${extensionId}/sidepanel.html`, { waitUntil: 'domcontentloaded' })
  assert.equal(await sidepanel.getByRole('heading', { name: '申请助手' }).count(), 1)
  assert.equal(await sidepanel.getByRole('button', { name: '连接 JobPilot' }).count(), 1)
  assert.equal(await sidepanel.locator('button[type="submit"]').count(), 0)
  assert.deepEqual(pageErrors, [])

  process.stdout.write(JSON.stringify({ status: 'PASS', extensionIdFormat: 'VALID', adapter: snapshot.adapterId,
    visibleCapture: true, hiddenDataCaptured: false, submitControls: 0, pageErrors: 0 }) + '\n')
} finally {
  if (context) await context.close()
  await rm(sandboxPath, { recursive: true, force: true })
}
