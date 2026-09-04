import './sidepanel.css'
import { api, ExtensionAuthenticationError, pair } from './api.js'
import { buildCapturePayload, validateCapturePayload } from './capture.js'
import type { ExtensionTokens, VisibleFields, VisibleJobSnapshot } from './types.js'

type RecordValue = Record<string, unknown>

let snapshot: VisibleJobSnapshot | null = null
let currentJobId: string | null = null
let workspace: RecordValue | null = null

const element = <T extends HTMLElement>(id: string) => document.getElementById(id) as T
const status = element<HTMLDivElement>('status')

document.addEventListener('DOMContentLoaded', () => void initialize())

async function initialize() {
  const stored = await chrome.storage.session.get('extensionTokens')
  const storedTokens = stored.extensionTokens as ExtensionTokens | undefined
  setPaired(Boolean(storedTokens), storedTokens)
  element<HTMLButtonElement>('pairButton').addEventListener('click', () => void run('正在配对…', pairDevice))
  element<HTMLButtonElement>('extractButton').addEventListener('click', () => void run('正在读取当前页面…', extractCurrent))
  element<HTMLButtonElement>('saveButton').addEventListener('click', () => void run('正在保存岗位…', saveCapture))
  element<HTMLButtonElement>('analyzeButton').addEventListener('click', () => void run('正在创建匹配分析…', analyze))
  element<HTMLButtonElement>('draftButton').addEventListener('click', () => void run('正在生成可编辑草稿…', createDraft))
  element<HTMLButtonElement>('queueButton').addEventListener('click', () => void run('正在加入 Assist 队列…', enqueue))
  element<HTMLButtonElement>('refreshButton').addEventListener('click', () => void run('正在刷新…', refreshWorkspace))
  element<HTMLButtonElement>('openButton').addEventListener('click', () => void chrome.runtime.sendMessage({ type: 'OPEN_JOBPILOT', path: currentJobId ? `/jobs/${currentJobId}` : '/jobs' }))
}

async function pairDevice() {
  const pairingCode = element<HTMLInputElement>('pairingCode').value.trim().toUpperCase()
  if (!pairingCode) throw new Error('请输入配对码。')
  const tokens = await pair(pairingCode)
  setPaired(true, tokens)
  showStatus(`已连接设备：${tokens.device.deviceName}`, 'success')
}

async function extractCurrent() {
  // Resolve the active tab from the side panel's own browser window. A Manifest V3
  // service worker has no stable "current window" and can otherwise select the
  // wrong window when DevTools or another browser window was focused recently.
  const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true })
  if (!activeTab?.id) throw new Error('未找到当前活动页面。')
  // Opening the extension action popup grants activeTab for this page. Persist
  // only the numeric tab id in session storage so the worker can bind the user
  // gesture to this extraction request without retaining browsing data.
  await chrome.storage.session.set({ activeExtractionTabId: activeTab.id })
  const response = await chrome.runtime.sendMessage({ type: 'EXTRACT_CURRENT_TAB', tabId: activeTab.id }) as VisibleJobSnapshot & { error?: string }
  if (response?.error) throw new Error(extractionErrorMessage(response.error))
  snapshot = response
  setValue('jobTitle', response.visibleFields.jobTitle)
  setValue('companyName', response.visibleFields.companyName)
  setValue('city', response.visibleFields.city || '')
  setValue('salaryText', response.visibleFields.salaryText || '')
  setValue('descriptionText', response.visibleFields.descriptionText)
  element('captureForm').classList.remove('hidden')
  element('captureWarnings').textContent = response.warnings.join(' ')
  showStatus(response.status === 'READY' ? '已提取，请检查后保存。' : '页面结构不确定，请补全后保存。', response.status === 'READY' ? 'success' : 'warning')
}

function extractionErrorMessage(code: string) {
  if (code === 'ACTIVE_TAB_PERMISSION_REQUIRED') {
    return 'Edge 尚未授予当前页临时读取权限。请关闭侧栏，再点击工具栏中的 JobPilot 图标重新打开。'
  }
  if (code === 'NO_ACTIVE_TAB') return '未找到当前活动页面，请切回岗位页后重试。'
  if (code === 'UNSUPPORTED_TAB') return '当前页面不是可读取的 HTTP(S) 页面。'
  if (code === 'VISIBLE_EXTRACTION_FAILED') return '页面读取失败，请刷新岗位页后重试。'
  return '当前页面不可读取，请打开一个 HTTP(S) 岗位页面。'
}

async function saveCapture() {
  if (!snapshot) throw new Error('请先提取当前页面。')
  const fields: VisibleFields = {
    jobTitle: value('jobTitle'), companyName: value('companyName'), city: value('city'),
    salaryText: value('salaryText'), descriptionText: value('descriptionText'),
    selectedText: snapshot.visibleFields.selectedText
  }
  const payload = buildCapturePayload(snapshot, fields)
  validateCapturePayload(payload)
  const result = await api<RecordValue>('/extension/job-captures', { method: 'POST', body: payload })
  currentJobId = nestedString(result, 'job', 'job', 'id')
  if (!currentJobId) throw new Error('岗位已保存，但响应中缺少岗位标识。')
  element('workspacePanel').classList.remove('hidden')
  await refreshWorkspace()
  showStatus('岗位已保存到 JobPilot。', 'success')
}

async function refreshWorkspace() {
  requireJob()
  workspace = await api<RecordValue>(`/extension/jobs/${currentJobId}/workspace`)
  renderWorkspace(workspace)
}

async function analyze() {
  requireJob()
  await api(`/extension/jobs/${currentJobId}:analyze`, {
    method: 'POST', key: crypto.randomUUID(),
    body: { resumeVersionId: recommendedResumeVersion(), force: false }
  })
  showStatus('匹配任务已创建；稍后刷新查看结果。', 'success')
}

async function createDraft() {
  requireJob()
  await api(`/extension/jobs/${currentJobId}/communication-drafts`, {
    method: 'POST', key: crypto.randomUUID(), body: { channel: 'BOSS', resumeVersionId: recommendedResumeVersion() }
  })
  await refreshWorkspace()
  showStatus('可编辑沟通草稿已生成，不会自动发送。', 'success')
}

async function enqueue() {
  requireJob()
  const latestMatch = objectValue(workspace, 'latestMatch')
  const latestDraft = objectValue(workspace, 'latestDraft')
  await api(`/extension/jobs/${currentJobId}/queue`, {
    method: 'POST', key: crypto.randomUUID(), body: {
      resumeVersionId: recommendedResumeVersion(),
      jobMatchId: stringValue(latestMatch, 'id'),
      draftId: stringValue(latestDraft, 'id'),
      priority: 70
    }
  })
  showStatus('已加入 Assist 队列，仍需在 JobPilot 中人工审批。', 'success')
}

function renderWorkspace(data: RecordValue) {
  const job = objectValue(objectValue(data, 'job'), 'job')
  const match = objectValue(data, 'latestMatch')
  const resume = objectValue(data, 'recommendedResume')
  const draft = objectValue(data, 'latestDraft')
  const sections = [
    summaryBlock('岗位', `${stringValue(job, 'title') || '—'} · ${nestedString(job, 'company', 'displayName') || '—'}`),
    summaryBlock('匹配', match ? `综合分 ${nestedString(match, 'scores', 'overall') || '—'} · ${stringValue(match, 'level') || '待复核'}` : '尚未分析'),
    summaryBlock('优势', evidenceText(match, 'advantages')),
    summaryBlock('缺口', evidenceText(match, 'gaps')),
    summaryBlock('风险', evidenceText(match, 'risks')),
    summaryBlock('简历', resume ? `${stringValue(resume, 'name') || '推荐简历'} · ${stringValue(resume, 'currentVersionName') || '当前版本'}` : '尚无可用简历'),
    summaryBlock('沟通草稿', draft ? `${displayText(stringValue(draft, 'status') || 'DRAFT')} · 仅供人工编辑` : '尚未生成')
  ]
  element('workspaceSummary').replaceChildren(...sections)
}

function summaryBlock(label: string, content: string) {
  const block = document.createElement('div')
  block.className = 'summary-row'
  const title = document.createElement('span')
  title.textContent = label
  const valueNode = document.createElement('p')
  valueNode.textContent = content
  block.append(title, valueNode)
  return block
}

function setPaired(paired: boolean, tokens?: ExtensionTokens) {
  element('pairingPanel').classList.toggle('hidden', paired)
  element('capturePanel').classList.toggle('hidden', !paired)
  const badge = element('connectionBadge')
  badge.textContent = paired ? `已配对${tokens ? ` · ${displayText(tokens.device.deviceName)}` : ''}` : '未配对'
  badge.classList.toggle('connected', paired)
  element('scopeSummary').textContent = tokens ? `设备 ${displayText(tokens.device.deviceName)} · ${tokens.scopes.map(displayText).join(' · ')} · 撤销授权请前往网页设置` : ''
}

async function run(progress: string, action: () => Promise<void>) {
  showStatus(progress)
  try { await action() } catch (error) {
    if (error instanceof ExtensionAuthenticationError) {
      snapshot = null
      currentJobId = null
      workspace = null
      element('workspacePanel').classList.add('hidden')
      element('captureForm').classList.add('hidden')
      setPaired(false)
    }
    showStatus(error instanceof Error ? error.message : '操作失败，请重试。', 'error')
  }
}

function showStatus(message: string, kind: 'success' | 'warning' | 'error' | '' = '') {
  status.textContent = message
  status.className = kind
}

function requireJob() { if (!currentJobId) throw new Error('请先保存当前岗位。') }
function value(id: string) { return element<HTMLInputElement | HTMLTextAreaElement>(id).value.trim() }
function setValue(id: string, newValue: string) { element<HTMLInputElement | HTMLTextAreaElement>(id).value = newValue }
function objectValue(source: RecordValue | null | undefined, key: string): RecordValue | null {
  const candidate = source?.[key]
  return candidate && typeof candidate === 'object' && !Array.isArray(candidate) ? candidate as RecordValue : null
}
function stringValue(source: RecordValue | null, key: string): string | null {
  const candidate = source?.[key]
  return candidate === null || candidate === undefined ? null : String(candidate)
}
function nestedString(source: RecordValue | null, ...keys: string[]): string | null {
  let current: unknown = source
  for (const key of keys) {
    if (!current || typeof current !== 'object' || Array.isArray(current)) return null
    current = (current as RecordValue)[key]
  }
  return current === null || current === undefined ? null : String(current)
}
function recommendedResumeVersion() {
  return stringValue(objectValue(workspace, 'recommendedResume'), 'currentVersionId') || undefined
}
function evidenceText(match: RecordValue | null, key: string) {
  const items = match?.[key]
  if (!Array.isArray(items) || items.length === 0) return '暂无'
  return items.slice(0, 3).map((item) => item && typeof item === 'object' ? stringValue(item as RecordValue, 'text') : null)
    .filter(Boolean).join('；') || '暂无'
}

function displayText(value: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿', READY: '就绪', NEED_REVIEW: '需要审核', SUCCESS: '成功', FAILED: '失败',
    S: 'S 级', A: 'A 级', B: 'B 级', C: 'C 级', D: 'D 级',
    JOB_CAPTURE: '岗位采集', MATCH_READ: '读取匹配', QUEUE_WRITE: '写入队列',
    DRAFT_WRITE: '写入草稿', ASSIST_PREPARE: '准备辅助材料',
    'Chrome Extension': '浏览器扩展', '浏览器扩展': '浏览器扩展'
  }
  return labels[value] || value
}
