import type { VisibleFields, VisibleJobSnapshot } from './types.js'

const MAX_DESCRIPTION = 20_000

export async function extractVisibleJob(doc: Document = document, currentUrl: string = window.location.href): Promise<VisibleJobSnapshot> {
  const selectedText = clean(doc.getSelection?.()?.toString() || '')
  const fixture = fixtureFields(doc)
  let adapterId: VisibleJobSnapshot['adapterId'] = 'GENERIC_VISIBLE_V1'
  let fields = fixture
  if (fixture.jobTitle && fixture.companyName && fixture.descriptionText) adapterId = 'FIXTURE_V1'
  else if (selectedText) {
    adapterId = 'MANUAL_SELECTION_V1'
    fields = genericFields(doc)
    fields.descriptionText = selectedText
    fields.selectedText = selectedText
  } else fields = genericFields(doc)
  const complete = Boolean(fields.jobTitle && fields.companyName && fields.descriptionText)
  const warnings = complete ? [] : ['无法识别当前页面结构，请检查并补全字段后再保存。']
  const capturedAt = new Date().toISOString().slice(0, 23)
  const normalized = JSON.stringify({ adapterId, currentUrl, fields })
  return {
    adapterId, adapterVersion: adapterId.toLowerCase(), status: complete ? 'READY' : 'NEEDS_REVIEW',
    platform: adapterId === 'FIXTURE_V1' ? 'DEMO_FIXTURE' : 'GENERIC_VISIBLE',
    pageUrl: currentUrl, capturedAt, visibleFields: fields,
    contentHash: await sha256(normalized), warnings
  }
}

function fixtureFields(doc: Document): VisibleFields {
  return {
    jobTitle: field(doc, 'jobTitle'), companyName: field(doc, 'companyName'), city: field(doc, 'city'),
    salaryText: field(doc, 'salaryText'), descriptionText: field(doc, 'descriptionText').slice(0, MAX_DESCRIPTION)
  }
}

function genericFields(doc: Document): VisibleFields {
  const heading = firstVisibleText(doc, ['h1', '[role="heading"][aria-level="1"]']) || clean(doc.title)
  const company = firstVisibleText(doc, ['[data-company]', '.company-name', '[class*="company"]'])
  const description = firstVisibleText(doc, ['[data-job-description]', 'article', 'main'])
  return { jobTitle: heading, companyName: company, descriptionText: description.slice(0, MAX_DESCRIPTION) }
}

function field(doc: Document, name: string): string {
  const element = doc.querySelector(`[data-jobpilot-field="${name}"]`)
  return visibleText(element)
}

function firstVisibleText(doc: Document, selectors: string[]): string {
  for (const selector of selectors) {
    for (const element of Array.from(doc.querySelectorAll(selector))) {
      const value = visibleText(element)
      if (value) return value
    }
  }
  return ''
}

function visibleText(element: Element | null): string {
  if (!element || isForbidden(element) || !isVisible(element)) return ''
  return clean(element.textContent || '')
}

function isForbidden(element: Element): boolean {
  return Boolean(element.closest('script,style,noscript,input,textarea,select,[hidden],[aria-hidden="true"]'))
}

function isVisible(element: Element): boolean {
  const html = element as HTMLElement
  const style = html.ownerDocument.defaultView?.getComputedStyle(html)
  if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false
  if (html.hasAttribute('hidden')) return false
  if (typeof html.getClientRects === 'function' && html.getClientRects().length === 0) {
    return html.ownerDocument.defaultView?.navigator.userAgent.includes('jsdom') === true
  }
  return true
}

function clean(value: string): string { return value.replace(/\s+/g, ' ').trim() }

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return Array.from(new Uint8Array(digest)).map((item) => item.toString(16).padStart(2, '0')).join('')
}
