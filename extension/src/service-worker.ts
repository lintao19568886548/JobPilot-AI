chrome.runtime.onInstalled.addListener(async () => {
  await chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' })
})

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'EXTRACT_CURRENT_TAB') {
    extractCurrentTab(message.tabId).then(sendResponse).catch((error: unknown) => sendResponse({ error: extractionErrorCode(error) }))
    return true
  }
  if (message?.type === 'OPEN_JOBPILOT') {
    const path = typeof message.path === 'string' && message.path.startsWith('/') ? message.path : '/jobs'
    chrome.tabs.create({ url: `http://127.0.0.1:5173${path}` }).then(() => sendResponse({ opened: true }))
    return true
  }
  return false
})

async function extractCurrentTab(requestedTabId: unknown) {
  if (!Number.isInteger(requestedTabId)) throw new Error('NO_ACTIVE_TAB')
  const tabId = requestedTabId as number
  const stored = await chrome.storage.session.get('activeExtractionTabId')
  if (stored.activeExtractionTabId !== tabId) throw new Error('ACTIVE_TAB_PERMISSION_REQUIRED')
  const tab = await chrome.tabs.get(tabId)
  // Chromium can omit tab.url when only activeTab (not the broad tabs permission)
  // is granted. The scripting API remains the authoritative permission check.
  if (tab.url && !/^https?:\/\//.test(tab.url)) throw new Error('UNSUPPORTED_TAB')
  await chrome.scripting.executeScript({ target: { tabId }, files: ['content-script.js'] })
  return chrome.tabs.sendMessage(tabId, { type: 'JOBPILOT_EXTRACT_VISIBLE' })
}

function extractionErrorCode(error: unknown) {
  const message = error instanceof Error ? error.message : String(error)
  if (message.includes('NO_ACTIVE_TAB') || message.includes('No tab with id')) return 'NO_ACTIVE_TAB'
  if (message.includes('UNSUPPORTED_TAB')) return 'UNSUPPORTED_TAB'
  if (/Cannot access|Missing host permission|permission/i.test(message)) return 'ACTIVE_TAB_PERMISSION_REQUIRED'
  return 'CURRENT_TAB_NOT_AVAILABLE'
}
