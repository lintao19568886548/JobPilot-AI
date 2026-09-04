<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import MatchAnalysisPanel from '../../components/matching/MatchAnalysisPanel.vue'
import { displayText } from '../../utils/displayText.js'
import {
  createJob, deleteJob, getImportErrors, getJob, ignoreJob, importJobFile,
  importJobUrl, listJobs, parseJob, restoreJob, startMatch, getMatchRun,
  retryMatchRun, listJobMatches, getJobMatch
} from '../../api/jobs.js'
import { apiErrorMessage } from '../../api/http.js'

const loading = ref(false)
const actionLoading = ref(false)
const jobs = ref([])
const selected = ref(null)
const nextCursor = ref(null)
const hasMore = ref(false)
const createVisible = ref(false)
const importVisible = ref(false)
const importTab = ref('url')
const importResult = ref(null)
const importErrors = ref([])
const selectedFile = ref(null)
const matchLoading = ref(false)
const activeRun = ref(null)
const currentMatch = ref(null)
const matchHistory = ref([])

const filters = reactive({ title: '', city: '', company: '', skill: '', status: '', parseStatus: '', sort: 'publish_desc', limit: 20 })
const manual = reactive({
  title: '', companyName: '', city: '', salaryText: '', education: '',
  experienceMinYears: null, experienceMaxYears: null, jobType: 'FULL_TIME',
  description: '', platform: 'MANUAL', platformJobId: '', sourceType: 'MANUAL',
  jobUrl: '', userInitiated: true, parseAfterCreate: true
})
const urlForm = reactive({ url: '', platform: 'DEMO_WEB', platformJobId: '', userInitiated: true, idempotencyKey: '' })

const statusLabel = { ACTIVE: '有效', DISCOVERED: '待处理', IGNORED: '已忽略', CLOSED: '已关闭' }
const parseLabel = { SUCCESS: '已解析', PARTIAL: '部分解析', FAILED: '解析失败', PENDING: '待解析', PROCESSING: '解析中' }
const salary = computed(() => {
  const job = selected.value?.job
  if (!job) return '—'
  if (job.salaryText) return job.salaryText
  if (job.salaryMin != null || job.salaryMax != null) return `${job.salaryMin ?? '—'} – ${job.salaryMax ?? '—'} ${job.currency || ''}`
  return '面议 / 未披露'
})

onMounted(() => load(true))

async function load(reset = true) {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value != null))
    if (!reset && nextCursor.value) params.cursor = nextCursor.value
    const { data } = await listJobs(params)
    jobs.value = reset ? data.data.items : [...jobs.value, ...data.data.items]
    nextCursor.value = data.data.nextCursor
    hasMore.value = data.data.hasMore
    if (reset && jobs.value.length) await selectJob(jobs.value[0].id)
    if (reset && !jobs.value.length) selected.value = null
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '岗位列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function selectJob(id) {
  try {
    activeRun.value = null
    const { data } = await getJob(id)
    selected.value = data.data
    await loadMatches(id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '岗位详情加载失败'))
  }
}

async function loadMatches(jobId) {
  try {
    const { data } = await listJobMatches(jobId)
    matchHistory.value = data.data
    currentMatch.value = data.data[0] || null
  } catch (error) {
    matchHistory.value = []
    currentMatch.value = null
    ElMessage.error(apiErrorMessage(error, '匹配历史加载失败'))
  }
}

async function runMatch(force = false) {
  if (!selected.value) return
  matchLoading.value = true
  try {
    const key = `match-ui-${selected.value.job.id}-${crypto.randomUUID()}`
    const { data } = await startMatch(selected.value.job.id, { force, idempotencyKey: key }, key)
    activeRun.value = data.data
    await waitForRun(data.data.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '匹配任务启动失败'))
  } finally {
    matchLoading.value = false
  }
}

async function waitForRun(runId) {
  for (let attempt = 0; attempt < 600; attempt += 1) {
    const { data } = await getMatchRun(runId)
    activeRun.value = data.data
    if (data.data.status === 'SUCCEEDED') {
      if (data.data.resultMatchId) {
        const response = await getJobMatch(data.data.resultMatchId)
        currentMatch.value = response.data.data
      }
      await loadMatches(selected.value.job.id)
      ElMessage.success('匹配完成并已持久化')
      return
    }
    if (['FAILED', 'DEAD'].includes(data.data.status)) {
      ElMessage.error(data.data.errorMessage || '匹配失败，可安全重试')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1000))
  }
  ElMessage.warning('匹配仍在后台执行，请稍后刷新')
}

async function retryMatch() {
  if (!activeRun.value) return
  matchLoading.value = true
  try {
    const { data } = await retryMatchRun(activeRun.value.id)
    activeRun.value = data.data
    await waitForRun(data.data.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '重试失败'))
  } finally {
    matchLoading.value = false
  }
}

async function viewHistoricalMatch(id) {
  const { data } = await getJobMatch(id)
  currentMatch.value = data.data
}

async function submitManual() {
  if (!manual.title || !manual.companyName || !manual.description) return ElMessage.warning('请填写岗位、公司和完整描述')
  actionLoading.value = true
  try {
    const payload = { ...manual, platformJobId: manual.platformJobId || null, jobUrl: manual.jobUrl || null }
    const { data } = await createJob(payload)
    createVisible.value = false
    ElMessage.success(data.data.created ? '岗位已创建并持久化' : `已合并重复来源：${data.data.dedupDecision}`)
    await load(true)
    await selectJob(data.data.job.job.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '岗位创建失败'))
  } finally {
    actionLoading.value = false
  }
}

async function submitUrl() {
  if (!urlForm.url) return ElMessage.warning('请输入 HTTP(S) 地址')
  actionLoading.value = true
  importErrors.value = []
  try {
    if (!urlForm.idempotencyKey) urlForm.idempotencyKey = `web-${crypto.randomUUID()}`
    const { data } = await importJobUrl(urlForm)
    importResult.value = data.data
    ElMessage.success('URL 导入已完成')
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, 'URL 导入失败'))
  } finally {
    actionLoading.value = false
  }
}

function chooseFile(event) {
  selectedFile.value = event.target.files?.[0] || null
}

async function submitFile() {
  if (!selectedFile.value) return ElMessage.warning('请选择 CSV 或 XLSX 文件')
  actionLoading.value = true
  importErrors.value = []
  try {
    const { data } = await importJobFile(selectedFile.value, `file-${selectedFile.value.name}-${selectedFile.value.size}-${selectedFile.value.lastModified}`)
    importResult.value = data.data
    if (data.data.failureCount) {
      const response = await getImportErrors(data.data.id)
      importErrors.value = response.data.data
    }
    ElMessage.success(`导入完成：成功 ${data.data.successCount}，失败 ${data.data.failureCount}`)
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '文件导入失败'))
  } finally {
    actionLoading.value = false
  }
}

async function rerunParse() {
  if (!selected.value) return
  actionLoading.value = true
  try {
    const { data } = await parseJob(selected.value.job.id)
    selected.value = data.data
    ElMessage.success(`解析完成：${parseLabel[data.data.job.parseStatus] || data.data.job.parseStatus}`)
    await load(false)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '解析失败'))
  } finally {
    actionLoading.value = false
  }
}

async function toggleIgnore() {
  if (!selected.value) return
  actionLoading.value = true
  try {
    const response = selected.value.job.status === 'IGNORED' ? await restoreJob(selected.value.job.id) : await ignoreJob(selected.value.job.id)
    selected.value = response.data.data
    ElMessage.success(selected.value.job.status === 'IGNORED' ? '岗位已忽略' : '岗位已恢复')
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '状态更新失败'))
  } finally {
    actionLoading.value = false
  }
}

async function removeSelected() {
  if (!selected.value) return
  await ElMessageBox.confirm('岗位将逻辑删除，解析与审计历史不会物理清除。', '删除岗位', { type: 'warning' })
  try {
    await deleteJob(selected.value.job.id)
    ElMessage.success('岗位已删除')
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '删除失败'))
  }
}

function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
function displaySalary(job) { return job.salaryText ? String(job.salaryText).replace(/^MANUAL\s+/i, '手动录入 ') : (job.salaryMin != null ? `${job.salaryMin / 1000}-${job.salaryMax / 1000}K` : '薪资未披露') }
function scoreValue(value) { return value == null ? '—' : Number(value).toFixed(1) }
function scorePercent(value) { return value == null ? 0 : Math.max(0, Math.min(100, Number(value))) }
const scoreDimensions = computed(() => currentMatch.value ? [
  ['技能', currentMatch.value.scores.skill, currentMatch.value.effectiveWeights.skill],
  ['向量语义', currentMatch.value.scores.embedding, currentMatch.value.effectiveWeights.embedding],
  ['LLM', currentMatch.value.scores.llm, currentMatch.value.effectiveWeights.llm],
  ['项目', currentMatch.value.scores.project, currentMatch.value.effectiveWeights.project],
  ['求职偏好', currentMatch.value.scores.preference, currentMatch.value.effectiveWeights.preference],
  ['公司', currentMatch.value.scores.company, currentMatch.value.effectiveWeights.company]
] : [])
</script>

<template>
  <section class="page-section job-center" v-loading="loading">
    <div class="page-heading">
      <div>
        <p class="eyebrow accent">阶段 3 · 可解释匹配</p>
        <h1>岗位中心</h1>
        <p>真实岗位事实、BGE-M3 语义向量、硬性筛选与可追溯评分。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="importVisible = true">导入岗位</el-button>
        <el-button type="primary" @click="createVisible = true">手动新增</el-button>
      </div>
    </div>

    <div class="job-filter-bar">
      <el-input v-model="filters.title" clearable placeholder="岗位关键词" @keyup.enter="load(true)" />
      <el-input v-model="filters.company" clearable placeholder="公司" @keyup.enter="load(true)" />
      <el-input v-model="filters.city" clearable placeholder="城市" @keyup.enter="load(true)" />
      <el-input v-model="filters.skill" clearable placeholder="技能，如 Java" @keyup.enter="load(true)" />
      <el-select v-model="filters.status" clearable placeholder="岗位状态">
        <el-option label="有效" value="ACTIVE" /><el-option label="待处理" value="DISCOVERED" /><el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-select v-model="filters.parseStatus" clearable placeholder="解析状态">
        <el-option label="已解析" value="SUCCESS" /><el-option label="部分解析" value="PARTIAL" /><el-option label="解析失败" value="FAILED" /><el-option label="待解析" value="PENDING" />
      </el-select>
      <el-select v-model="filters.sort" @change="load(true)"><el-option label="发布时间" value="publish_desc" /><el-option label="最近更新" value="updated_desc" /></el-select>
      <el-button @click="load(true)">筛选</el-button>
    </div>

    <div class="job-workspace">
      <aside class="surface job-list-panel">
        <div class="panel-title"><h2>岗位列表</h2><span>{{ jobs.length }} 条已加载</span></div>
        <button v-for="item in jobs" :key="item.id" class="job-list-item" :class="{ active: selected?.job.id === item.id }" @click="selectJob(item.id)">
          <div><strong>{{ item.title }}</strong><span>{{ item.company.displayName }}</span></div>
          <p>{{ item.city || '地点未披露' }} · {{ displaySalary(item) }}</p>
          <div class="job-tags"><i :class="item.parseStatus.toLowerCase()">{{ parseLabel[item.parseStatus] || item.parseStatus }}</i><i>{{ item.sourceCount }} 来源</i><i>{{ item.skillCount }} 技能</i></div>
        </button>
        <div v-if="!jobs.length" class="empty-state compact">暂无真实岗位，使用“手动新增”或“导入岗位”开始。</div>
        <el-button v-if="hasMore" class="load-more" text @click="load(false)">加载下一页</el-button>
      </aside>

      <main class="surface job-detail-panel">
        <template v-if="selected">
          <header class="job-detail-heading">
            <div><span class="section-kicker">{{ selected.job.company.industry || '行业未设置' }}</span><h2>{{ selected.job.title }}</h2><p>{{ selected.job.company.displayName }} · {{ selected.job.city || '地点未披露' }}</p></div>
            <div class="job-detail-actions"><el-button size="small" :loading="actionLoading" @click="rerunParse">重新解析</el-button><el-button size="small" @click="toggleIgnore">{{ selected.job.status === 'IGNORED' ? '恢复' : '忽略' }}</el-button><el-button size="small" text type="danger" @click="removeSelected">删除</el-button></div>
          </header>
          <div class="job-facts">
            <div><span>薪资</span><strong>{{ salary }}</strong></div><div><span>学历</span><strong>{{ selected.job.education ? displayText(selected.job.education) : '未披露' }}</strong></div><div><span>经验</span><strong>{{ selected.job.experienceMinYears ?? '—' }}–{{ selected.job.experienceMaxYears ?? '—' }} 年</strong></div><div><span>工作方式</span><strong>{{ displayText(selected.job.remoteType) }}</strong></div>
          </div>
          <div class="parser-strip"><span>解析状态 <strong>{{ parseLabel[selected.job.parseStatus] || displayText(selected.job.parseStatus) }}</strong></span><span>模式 <strong>{{ displayText(selected.job.parserMode) }}</strong></span><span>版本 <strong>{{ selected.job.parserVersion || '—' }}</strong></span><span>更新时间 <strong>{{ formatTime(selected.job.updatedAt) }}</strong></span></div>

          <section class="job-section"><h3>技能要求</h3><div v-if="selected.skills.length" class="skill-cloud"><span v-for="skill in selected.skills" :key="skill.id" :class="skill.requirementType.toLowerCase()"><strong>{{ skill.displayName }}</strong><small>{{ skill.requirementType === 'MUST_HAVE' ? '必须' : skill.requirementType === 'NICE_TO_HAVE' ? '加分' : '相关' }} · {{ skill.importance }}</small></span></div><p v-else class="muted">未识别技能，可检查原始描述后重新解析。</p></section>
          <section class="job-section"><h3>岗位职责</h3><ul v-if="selected.responsibilities.length"><li v-for="item in selected.responsibilities" :key="item">{{ item }}</li></ul><p v-else class="muted">暂无结构化职责。</p></section>
          <section class="job-section"><h3>要求与描述</h3><ul v-if="selected.requirements.length"><li v-for="item in selected.requirements" :key="item">{{ item }}</li></ul><pre class="job-description">{{ selected.descriptionClean }}</pre></section>
          <section class="job-section"><h3>来源与解析追溯</h3><div class="source-line" v-for="source in selected.sources" :key="source.id"><span>{{ displayText(source.platform) }} · {{ displayText(source.sourceType) }}</span><a v-if="source.jobUrl" :href="source.jobUrl" target="_blank" rel="noreferrer">打开原网页 ↗</a><time>{{ formatTime(source.collectedAt) }}</time></div><p class="trace-note">解析运行 {{ selected.parseRuns.length }} 次 · 原文哈希 {{ selected.rawContentHash }}</p></section>
        </template>
        <div v-else class="empty-state centered">选择岗位后查看结构化详情。</div>
      </main>

      <MatchAnalysisPanel :enabled="Boolean(selected)" :current-match="currentMatch" :active-run="activeRun"
        :history="matchHistory" :loading="matchLoading" @run="runMatch" @retry="retryMatch" @view-history="viewHistoricalMatch" />
    </div>

    <el-dialog v-model="createVisible" title="手动新增岗位" width="760px">
      <el-form label-position="top"><div class="form-grid two"><el-form-item label="岗位名称"><el-input v-model="manual.title" maxlength="200" /></el-form-item><el-form-item label="公司"><el-input v-model="manual.companyName" maxlength="200" /></el-form-item><el-form-item label="城市"><el-input v-model="manual.city" /></el-form-item><el-form-item label="薪资原文"><el-input v-model="manual.salaryText" placeholder="15-25K·14薪" /></el-form-item><el-form-item label="学历"><el-select v-model="manual.education" clearable><el-option label="本科" value="BACHELOR" /><el-option label="硕士" value="MASTER" /><el-option label="博士" value="DOCTOR" /></el-select></el-form-item><el-form-item label="来源岗位 ID"><el-input v-model="manual.platformJobId" /></el-form-item><el-form-item class="wide" label="原始岗位描述"><el-input v-model="manual.description" type="textarea" :rows="9" maxlength="100000" show-word-limit /></el-form-item></div></el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitManual">保存并解析</el-button></template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入岗位" width="720px">
      <el-tabs v-model="importTab">
        <el-tab-pane label="URL 导入" name="url"><el-form label-position="top"><el-form-item label="公开 HTTP(S) 岗位地址"><el-input v-model="urlForm.url" placeholder="https://..." /></el-form-item><div class="form-grid two"><el-form-item label="平台"><el-input v-model="urlForm.platform" /></el-form-item><el-form-item label="平台岗位 ID（可选）"><el-input v-model="urlForm.platformJobId" /></el-form-item></div><p class="import-hint">服务端执行 SSRF 防护、响应大小与类型限制；不会读取 Cookie 或浏览器凭证。</p><el-button type="primary" :loading="actionLoading" @click="submitUrl">安全抓取并解析</el-button></el-form></el-tab-pane>
        <el-tab-pane label="CSV / XLSX" name="file"><div class="file-drop"><input type="file" accept=".csv,.xlsx" @change="chooseFile"><strong>{{ selectedFile?.name || '选择 CSV 或 XLSX 文件' }}</strong><span>逐行校验，错误不会阻断其他有效行。</span></div><el-button type="primary" :loading="actionLoading" @click="submitFile">上传并导入</el-button></el-tab-pane>
      </el-tabs>
      <div v-if="importResult" class="import-result"><strong>{{ displayText(importResult.status) }}</strong><span>总计 {{ importResult.totalCount }} · 成功 {{ importResult.successCount }} · 失败 {{ importResult.failureCount }}</span><small>任务 {{ importResult.id }}</small></div>
      <div v-if="importErrors.length" class="import-errors"><div v-for="error in importErrors" :key="error.id"><strong>第 {{ error.rowNumber }} 行 · {{ error.errorCode }}</strong><span>{{ error.errorMessage }}</span></div></div>
    </el-dialog>
  </section>
</template>
