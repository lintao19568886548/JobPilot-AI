<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '../../utils/elementFeedback.js'
import { useRoute } from 'vue-router'
import { listJobs } from '../../api/jobs.js'
import { listResumes } from '../../api/resume.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayBoolean, displayText } from '../../utils/displayText.js'
import {
  approveCommunicationDraft, approveTailorRun, createCommunicationDraft, createTailorRun,
  getEvidenceLedger, listCommunicationDrafts, listTailorRuns, markCommunicationDraftUsed,
  refreshEvidenceLedger
} from '../../api/tailoring.js'

const route = useRoute()
const loading = ref(true)
const working = ref(false)
const jobs = ref([])
const resumes = ref([])
const evidence = ref({ items: [], count: 0, snapshotHash: '' })
const runs = ref([])
const drafts = ref([])
const selectedRun = ref(null)
const selectedDraft = ref(null)
const form = reactive({ jobId: route.query.jobId || '', strategy: 'BALANCED', targetResumeId: '' })
const draftForm = reactive({ channel: 'BOSS', purpose: 'APPLICATION' })

const derivedResumes = computed(() => resumes.value.filter((item) => !item.master && item.status === 'ACTIVE'))
const selectedJob = computed(() => jobs.value.find((item) => item.id === form.jobId))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [jobResult, resumeResult, ledgerResult, runResult, draftResult] = await Promise.all([
      listJobs({ limit: 100, sort: 'updated_desc' }), listResumes(), getEvidenceLedger(), listTailorRuns(), listCommunicationDrafts()
    ])
    jobs.value = jobResult.data.data.items || []
    resumes.value = resumeResult.data.data || []
    evidence.value = ledgerResult.data.data
    runs.value = runResult.data.data || []
    drafts.value = draftResult.data.data || []
    if (!form.jobId) form.jobId = jobs.value[0]?.id || ''
    if (!form.targetResumeId) form.targetResumeId = derivedResumes.value[0]?.id || ''
    selectedRun.value = runs.value[0] || null
    selectedDraft.value = drafts.value[0] || null
  } catch (error) { ElMessage.error(apiErrorMessage(error, 'AI 工作室加载失败')) }
  finally { loading.value = false }
}

async function refreshEvidence() {
  working.value = true
  try {
    const { data } = await refreshEvidenceLedger()
    evidence.value = data.data
    ElMessage.success(`事实证据账本已刷新：${data.data.count} 条当前事实`)
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function runTailor() {
  if (!form.jobId) return ElMessage.warning('请先选择岗位')
  working.value = true
  try {
    const { data } = await createTailorRun(form.jobId, {
      baseResumeVersionId: null, targetResumeId: form.targetResumeId || null, strategy: form.strategy
    }, `tailor-ui-${crypto.randomUUID()}`)
    selectedRun.value = data.data
    await reloadHistories()
    ElMessage.success('定制方案已生成，请逐项审查后再批准')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function approveRun() {
  if (!selectedRun.value) return
  working.value = true
  try {
    const { data } = await approveTailorRun(selectedRun.value.id, { versionName: `${selectedRun.value.jobTitle} · 定制版` })
    selectedRun.value = data.data
    await reloadHistories()
    ElMessage.success('已创建新的不可变简历版本，主简历未被修改')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function generateDraft() {
  if (!form.jobId) return ElMessage.warning('请先选择岗位')
  working.value = true
  try {
    const { data } = await createCommunicationDraft(form.jobId, {
      channel: draftForm.channel, purpose: draftForm.purpose, applicationId: null,
      resumeVersionId: selectedRun.value?.approvedResumeVersionId || null, recruiterId: null
    }, `draft-ui-${crypto.randomUUID()}`)
    selectedDraft.value = data.data
    await reloadHistories()
    ElMessage.success('仅生成草稿；没有发送任何外部消息')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function approveDraft() {
  working.value = true
  try {
    const { data } = await approveCommunicationDraft(selectedDraft.value.id, selectedDraft.value.version)
    selectedDraft.value = data.data
    await reloadHistories()
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function markUsed() {
  working.value = true
  try {
    const { data } = await markCommunicationDraftUsed(selectedDraft.value.id, selectedDraft.value.version)
    selectedDraft.value = data.data
    await reloadHistories()
    ElMessage.success('已记录为用户实际使用；系统仍未发送消息')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
  finally { working.value = false }
}

async function reloadHistories() {
  const [runResult, draftResult, resumeResult] = await Promise.all([listTailorRuns(), listCommunicationDrafts(), listResumes()])
  runs.value = runResult.data.data || []
  drafts.value = draftResult.data.data || []
  resumes.value = resumeResult.data.data || []
}

function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
</script>

<template>
  <section v-loading="loading" class="page-section ai-studio-page">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 6 · 证据约束 AI</p><h1>AI 工作室</h1><p>先引用候选人事实，再生成可审查修改与沟通草稿。AI 不会发送消息，也不会改写主简历。</p></div>
      <el-button :loading="working" @click="refreshEvidence">刷新事实证据 · {{ evidence.count }}</el-button>
    </div>

    <div class="ai-studio-config surface">
      <label>目标岗位<el-select v-model="form.jobId" filterable><el-option v-for="job in jobs" :key="job.id" :label="`${job.title} · ${job.company?.name || '未知公司'}`" :value="job.id" /></el-select></label>
      <label>定制策略<el-select v-model="form.strategy"><el-option label="均衡" value="BALANCED" /><el-option label="ATS 优化" value="ATS" /><el-option label="精简" value="CONCISE" /></el-select></label>
      <label>目标简历<el-select v-model="form.targetResumeId" clearable placeholder="批准时自动创建"><el-option v-for="item in derivedResumes" :key="item.id" :label="item.name" :value="item.id" /></el-select></label>
      <el-button type="primary" :loading="working" @click="runTailor">生成可审查定制方案</el-button>
    </div>

    <div class="ai-studio-grid">
      <aside class="surface studio-history">
        <div class="panel-title"><span>定制记录</span><strong>{{ runs.length }}</strong></div>
        <button v-for="run in runs" :key="run.id" :class="{ active: selectedRun?.id === run.id }" @click="selectedRun=run">
          <strong>{{ run.jobTitle }}</strong><span>{{ displayText(run.strategy) }} · {{ displayText(run.status) }}</span><small>{{ time(run.createdAt) }}</small>
        </button>
      </aside>

      <main class="surface tailor-review">
        <template v-if="selectedRun">
          <header><div><span class="section-kicker">批准前审核</span><h2>{{ selectedRun.jobTitle }}</h2><p>{{ selectedRun.companyName }} · {{ selectedRun.promptVersion }} · {{ displayText(selectedRun.executionMode) }}</p></div><div class="tag-row"><span class="plain-tag accent-tag">{{ displayText(selectedRun.truthCheckStatus) }}</span><span class="plain-tag">{{ displayText(selectedRun.status) }}</span></div></header>
          <article v-for="change in selectedRun.changes" :key="change.id" class="tailor-change">
            <div class="change-meta"><strong>{{ displayText(change.sectionType) }}</strong><span>{{ displayText(change.operation) }}</span></div>
            <div class="change-diff"><div><small>修改前</small><p>{{ change.before || '原区块无可读文本' }}</p></div><div><small>修改后</small><p>{{ change.after }}</p></div></div>
            <p class="change-reason">{{ change.reason }}</p>
            <div class="evidence-refs"><span v-for="ref in change.evidenceRefs" :key="ref">{{ ref }}</span></div>
          </article>
          <div class="approval-boundary"><div><strong>批准后才会创建新版本</strong><p>旧版本与主简历永远保留，不会被覆盖。</p></div><el-button type="primary" :disabled="selectedRun.status !== 'READY'" :loading="working" @click="approveRun">批准并创建版本</el-button></div>
        </template>
        <div v-else class="empty-state centered">选择岗位并生成第一条定制记录</div>
      </main>
    </div>

    <section class="surface communication-studio">
      <header><div><span class="section-kicker">仅生成草稿 · 绝不自动发送</span><h2>沟通草稿</h2><p>{{ selectedJob?.title || '选择岗位' }} · 生成、批准、标记使用都不会调用外部发送。</p></div><div class="draft-controls"><el-select v-model="draftForm.channel"><el-option v-for="channel in ['BOSS','LIEPIN','EMAIL','WECHAT','THANK_YOU','FOLLOW_UP','OFFER']" :key="channel" :label="displayText(channel)" :value="channel" /></el-select><el-button type="primary" :loading="working" @click="generateDraft">生成草稿</el-button></div></header>
      <div class="draft-workspace">
        <aside><button v-for="draft in drafts" :key="draft.id" :class="{ active: selectedDraft?.id === draft.id }" @click="selectedDraft=draft"><strong>{{ displayText(draft.channel) }} · {{ draft.jobTitle }}</strong><span>{{ displayText(draft.status) }} · {{ draft.charCount }} 字</span></button></aside>
        <main v-if="selectedDraft"><div class="draft-status"><span class="plain-tag">{{ displayText(selectedDraft.status) }}</span><span class="plain-tag accent-tag">{{ displayText(selectedDraft.truthCheckStatus) }}</span><span>已外部发送：{{ displayBoolean(selectedDraft.externallySent) }}</span></div><pre>{{ selectedDraft.content }}</pre><div class="evidence-refs"><span v-for="ref in selectedDraft.evidenceRefs" :key="ref">{{ ref }}</span></div><div class="detail-actions"><el-button :disabled="selectedDraft.status !== 'DRAFT'" @click="approveDraft">批准草稿</el-button><el-button :disabled="selectedDraft.status !== 'APPROVED'" @click="markUsed">标记用户已使用</el-button></div></main>
        <div v-else class="empty-state centered">尚未生成沟通草稿</div>
      </div>
    </section>
  </section>
</template>
