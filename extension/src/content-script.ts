import { extractVisibleJob } from './adapters.js'

declare global { interface Window { __jobpilotContentInstalled?: boolean } }

if (!window.__jobpilotContentInstalled) {
  window.__jobpilotContentInstalled = true
  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type !== 'JOBPILOT_EXTRACT_VISIBLE') return false
    extractVisibleJob().then(sendResponse).catch(() => sendResponse({ error: 'VISIBLE_EXTRACTION_FAILED' }))
    return true
  })
}
