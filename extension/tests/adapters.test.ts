import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { JSDOM } from 'jsdom'
import { describe, expect, it } from 'vitest'
import { extractVisibleJob } from '../src/adapters.js'

describe('visible job extraction', () => {
  it('extracts only fixture fields visible to the user', async () => {
    const html = readFileSync(resolve('fixtures/job-page.html'), 'utf8')
    const dom = new JSDOM(html, { url: 'http://127.0.0.1:8020/fixtures/job-page.html', pretendToBeVisual: true })

    const snapshot = await extractVisibleJob(dom.window.document, dom.window.location.href)

    expect(snapshot.adapterId).toBe('FIXTURE_V1')
    expect(snapshot.status).toBe('READY')
    expect(snapshot.platform).toBe('DEMO_FIXTURE')
    expect(snapshot.visibleFields.jobTitle).toBe('Senior Java Backend Engineer')
    expect(snapshot.visibleFields.descriptionText).toContain('Spring Boot')
    expect(JSON.stringify(snapshot)).not.toContain('never-read-this-password')
    expect(JSON.stringify(snapshot)).not.toContain('session-secret')
    expect(JSON.stringify(snapshot)).not.toContain('authorization-secret')
  })

  it('marks an unknown incomplete page for review', async () => {
    const dom = new JSDOM('<main><h1>Unstructured role</h1></main>', { url: 'https://jobs.example.test/1', pretendToBeVisual: true })
    const snapshot = await extractVisibleJob(dom.window.document, dom.window.location.href)
    expect(snapshot.status).toBe('NEEDS_REVIEW')
    expect(snapshot.warnings).toHaveLength(1)
  })

  it('uses the conservative generic visible adapter without hidden input values', async () => {
    const dom = new JSDOM('<main><h1>Platform Engineer</h1><p data-company>Visible Company</p><article>Visible job description</article><input type="password" value="secret"></main>',
      { url: 'https://jobs.example.test/2', pretendToBeVisual: true })
    const snapshot = await extractVisibleJob(dom.window.document, dom.window.location.href)
    expect(snapshot.adapterId).toBe('GENERIC_VISIBLE_V1')
    expect(snapshot.status).toBe('READY')
    expect(snapshot.visibleFields.companyName).toBe('Visible Company')
    expect(JSON.stringify(snapshot)).not.toContain('secret')
  })

  it('prefers an explicit visible user selection and requires review when metadata is incomplete', async () => {
    const dom = new JSDOM('<main><h1>Selected role</h1><p id="selection">User selected this visible job description.</p></main>',
      { url: 'https://jobs.example.test/3', pretendToBeVisual: true })
    const range = dom.window.document.createRange()
    range.selectNodeContents(dom.window.document.getElementById('selection')!)
    dom.window.getSelection()?.addRange(range)
    const snapshot = await extractVisibleJob(dom.window.document, dom.window.location.href)
    expect(snapshot.adapterId).toBe('MANUAL_SELECTION_V1')
    expect(snapshot.visibleFields.selectedText).toBe('User selected this visible job description.')
    expect(snapshot.status).toBe('NEEDS_REVIEW')
  })
})
