import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('extension UI safety boundary', () => {
  it('has no external submission or message-send control', () => {
    const html = readFileSync('sidepanel.html', 'utf8')
    expect(html).not.toMatch(/id="[^"]*(submit|send)[^"]*"/i)
    expect(html).not.toMatch(/type="submit"/i)
  })

  it('keeps tokens in session-only extension storage', () => {
    const api = readFileSync('src/api.ts', 'utf8')
    expect(api).toContain('chrome.storage.session')
    expect(api).not.toContain('chrome.storage.local')
    expect(api).not.toContain('chrome.storage.sync')
  })

  it('resolves the active tab in the side panel window and passes an explicit tab id', () => {
    const sidepanel = readFileSync('src/sidepanel.ts', 'utf8')
    const worker = readFileSync('src/service-worker.ts', 'utf8')
    expect(sidepanel).toContain('active: true, currentWindow: true')
    expect(sidepanel).toContain('activeExtractionTabId: activeTab.id')
    expect(sidepanel).toContain("type: 'EXTRACT_CURRENT_TAB', tabId: activeTab.id")
    expect(worker).toContain('chrome.tabs.get(tabId)')
    expect(worker).toContain("if (tab.url && !/^https?:\\/\\//.test(tab.url))")
    expect(worker).not.toContain('!tab.url')
  })

  it('uses the native action popup instead of programmatic side panel opening', () => {
    const manifest = readFileSync('manifest.json', 'utf8')
    const worker = readFileSync('src/service-worker.ts', 'utf8')
    expect(manifest).toContain('"default_popup": "sidepanel.html"')
    expect(worker).not.toContain('chrome.sidePanel.open')
    expect(worker).not.toContain('chrome.action.onClicked')
  })
})
