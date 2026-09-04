<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import { useRoute } from 'vue-router'
import MatchAnalysisPanel from '../../components/matching/MatchAnalysisPanel.vue'
import { apiErrorMessage } from '../../api/http.js'
import {
  createRecommendationRefresh, favoriteRecommendation, getRecommendation,
  getRecommendationCapabilities, getRecommendationRefresh, ignoreRecommendation,
  listRecommendations, restoreRecommendation, unfavoriteRecommendation
} from '../../api/recommendations.js'
import { getJobMatch, getMatchRun, listJobMatches, retryMatchRun, startMatch } from '../../api/jobs.js'
import { enqueueApplication } from '../../api/applications.js'
import { displayText } from '../../utils/displayText.js'

const route = useRoute()
const loading = ref(true)
const actionLoading = ref(false)
const refreshLoading = ref(false)
const recommendations = ref([])
const selectedId = ref(null)
const detail = ref(null)
const history = ref([])
const activeRun = ref(null)
const refreshRun = ref(null)
const nextCursor = ref(null)
const hasMore = ref(false)
const total = ref(0)
const capabilities = ref(null)

const filters = reactive({
  view: 'ALL', level: '', hardFilter: '', recommendation: '', city: '',
  companyName: '', skill: route.query.skill || '', keyword: '', sort: 'AI_RECOMMENDED', limit: 20
})

const views = [
  ['ALL', '全部'], ['TOP', 'S/A/B'], ['UNEVALUATED', '未评估'],
  ['FAVORITE', '收藏'], ['IGNORED', '忽略']
]

onMounted(async () => {
  if (['FAVORITE', 'IGNORED', 'UNEVALUATED', 'TOP'].includes(route.query.status)) filters.view = route.query.status
  if (['S', 'A', 'B', 'C', 'D'].includes(route.query.status)) filters.level = route.query.status
  await Promise.all([load(true), loadCapabilities()])
})

async function loadCapabilities() {
  try {
    const { data } = await getRecommendationCapabilities()
    capabilities.value = data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '推荐能力加载失败'))
  }
}

async function load(reset = true) {
  loading.value = true
  try {
    if (reset) {
      recommendations.value = []
      nextCursor.value = null
    }
    const params = Object.fromEntries(Object.entries({ ...filters, cursor: reset ? null : nextCursor.value })
      .filter(([, value]) => value !== '' && value !== null && value !== undefined))
    const { data } = await listRecommendations(params)
    recommendations.value = reset ? data.data.items : [...recommendations.value, ...data.data.items]
    nextCursor.value = data.data.nextCursor
    hasMore.value = data.data.hasMore
    total.value = data.data.total
    const requestedJob = route.query.jobId
    const preferred = recommendations.value.find((item) => item.job.id === requestedJob)
      || recommendations.value.find((item) => item.id === selectedId.value)
      || recommendations.value[0]
    if (preferred) await selectRecommendation(preferred.id)
    else {
      selectedId.value = null
      detail.value = null
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '推荐列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function selectRecommendation(id) {
  selectedId.value = id
  activeRun.value = null
  try {
    const { data } = await getRecommendation(id)
    detail.value = data.data
    const jobId = data.data.recommendation.job.id
    const matchResponse = await listJobMatches(jobId)
    history.value = matchResponse.data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '推荐详情加载失败'))
  }
}

function setView(view) {
  filters.view = view
  load(true)
}

async function toggleFavorite(item) {
  actionLoading.value = true
  try {
    const response = item.favorite
      ? await unfavoriteRecommendation(item.id, item.version)
      : await favoriteRecommendation(item.id, item.version)
    ElMessage.success(response.data.data.favorite ? '已收藏' : '已取消收藏')
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏状态更新失败'))
  } finally {
    actionLoading.value = false
  }
}

async function ignoreItem(item) {
  try {
    const { value } = await ElMessageBox.prompt('可选：说明该岗位为什么不适合当前求职方向。', '忽略推荐', {
      inputPlaceholder: '例如：方向不符、薪资不符、地点不合适', inputValidator: (text) => !text || text.length <= 500 || '最多 500 字'
    })
    actionLoading.value = true
    await ignoreRecommendation(item.id, item.version, value || null)
    ElMessage.success('推荐已忽略，岗位和 Match 历史仍然保留')
    await load(true)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '忽略失败'))
  } finally {
    actionLoading.value = false
  }
}

async function restoreItem(item) {
  actionLoading.value = true
  try {
    await restoreRecommendation(item.id, item.version)
    ElMessage.success('推荐已恢复')
    await load(true)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '恢复失败'))
  } finally {
    actionLoading.value = false
  }
}

async function addToQueue(item) {
  actionLoading.value = true
  try {
    await enqueueApplication({
      jobId: item.job.id,
      jobMatchId: item.match?.id || null,
      mode: 'MANUAL',
      priority: ['S', 'A'].includes(item.match?.level) ? 80 : 50,
      allowUnevaluated: !item.match
    }, `queue-ui-${crypto.randomUUID()}`)
    ElMessage.success('已加入 Application Queue，尚未进行任何外部投递')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '加入队列失败'))
  } finally {
    actionLoading.value = false
  }
}

async function refreshRecommendations() {
  refreshLoading.value = true
  try {
    const key = `recommendation-ui-${crypto.randomUUID()}`
    const { data } = await createRecommendationRefresh({ onlyUnevaluated: false, force: false, limit: 100, idempotencyKey: key }, key)
    refreshRun.value = data.data
    await waitForRefresh(data.data.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '批量刷新启动失败'))
  } finally {
    refreshLoading.value = false
  }
}

async function waitForRefresh(id) {
  for (let attempt = 0; attempt < 700; attempt += 1) {
    const { data } = await getRecommendationRefresh(id)
    refreshRun.value = data.data
    if (['SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED'].includes(data.data.status)) {
      const message = `推荐刷新完成：成功 ${data.data.succeededCount}，复用 ${data.data.reusedCount}，失败 ${data.data.failedCount}`
      data.data.failedCount ? ElMessage.warning(message) : ElMessage.success(message)
      await load(true)
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1000))
  }
  ElMessage.warning('批量刷新仍在后台运行')
}

async function runMatch(force) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    const jobId = detail.value.recommendation.job.id
    const key = `recommendation-match-${crypto.randomUUID()}`
    const { data } = await startMatch(jobId, { force, idempotencyKey: key }, key)
    activeRun.value = data.data
    await waitForMatch(data.data.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '匹配任务启动失败'))
  } finally {
    actionLoading.value = false
  }
}

async function waitForMatch(id) {
  for (let attempt = 0; attempt < 700; attempt += 1) {
    const { data } = await getMatchRun(id)
    activeRun.value = data.data
    if (data.data.status === 'SUCCEEDED') {
      await load(true)
      ElMessage.success('匹配完成，推荐投影已同步')
      return
    }
    if (['FAILED', 'DEAD'].includes(data.data.status)) {
      ElMessage.error(data.data.errorMessage || '匹配失败，可安全重试')
      return
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1000))
  }
}

async function retryMatch() {
  if (!activeRun.value) return
  const { data } = await retryMatchRun(activeRun.value.id)
  activeRun.value = data.data
  await waitForMatch(data.data.id)
}

async function viewHistory(id) {
  const { data } = await getJobMatch(id)
  if (detail.value) detail.value.matchAnalysis = data.data
}

function score(value) { return value == null ? '—' : Number(value).toFixed(1) }
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
function salary(job) { return job.salaryText ? String(job.salaryText).replace(/^MANUAL\s+/i, '手动录入 ') : (job.salaryMin != null ? `${Number(job.salaryMin / 1000).toFixed(0)}–${Number(job.salaryMax / 1000).toFixed(0)}K` : '薪资未披露') }
</script>

<template>
  <section class="page-section recommendation-page" v-loading="loading">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 4 · 每日求职决策</p><h1>AI 岗位推荐</h1><p>基于最新不可变匹配结果的真实推荐投影；排序不会修改原始分数。</p></div>
      <el-button type="primary" :loading="refreshLoading" @click="refreshRecommendations">刷新全部推荐</el-button>
    </div>

    <div v-if="refreshRun" class="refresh-status-line">
      <strong>{{ displayText(refreshRun.status) }}</strong><span>总计 {{ refreshRun.totalCount }} · 成功 {{ refreshRun.succeededCount }} · 复用 {{ refreshRun.reusedCount }} · 失败 {{ refreshRun.failedCount }}</span><small>{{ refreshRun.id }}</small>
    </div>

    <div class="recommendation-tabs">
      <button v-for="tab in views" :key="tab[0]" :class="{ active: filters.view === tab[0] }" @click="setView(tab[0])">{{ tab[1] }}</button>
      <router-link to="/applications" class="application-tab-link">已投递 / 队列 <small>阶段 5</small></router-link>
      <span>{{ total }} 条</span>
    </div>

    <div class="recommendation-filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="岗位、公司或技能" @keyup.enter="load(true)" />
      <el-input v-model="filters.city" clearable placeholder="城市" @keyup.enter="load(true)" />
      <el-input v-model="filters.skill" clearable placeholder="技能" @keyup.enter="load(true)" />
      <el-select v-model="filters.level" clearable placeholder="等级"><el-option v-for="level in ['S','A','B','C','D']" :key="level" :label="level" :value="level" /></el-select>
      <el-select v-model="filters.sort" @change="load(true)"><el-option label="AI 推荐顺序" value="AI_RECOMMENDED" /><el-option label="匹配度" value="MATCH_DESC" /><el-option label="发布时间" value="PUBLISH_DESC" /><el-option label="薪资" value="SALARY_DESC" /><el-option label="公司" value="COMPANY_ASC" /><el-option label="城市" value="CITY_ASC" /></el-select>
      <el-button @click="load(true)">应用筛选</el-button>
    </div>

    <div class="recommendation-workspace">
      <aside class="surface recommendation-list">
        <button v-for="item in recommendations" :key="item.id" :class="{ active: selectedId === item.id, ignored: item.status === 'IGNORED' }" @click="selectRecommendation(item.id)">
          <div class="recommendation-score"><strong>{{ score(item.match?.overallScore) }}</strong><i :class="`level-${(item.match?.level || 'na').toLowerCase()}`">{{ item.match?.level || '—' }}</i></div>
          <div><strong>{{ item.job.title }}</strong><span>{{ item.job.companyName }} · {{ item.job.city || '地点未披露' }}</span><small>{{ salary(item.job) }} · {{ displayText(item.status) }}</small></div>
          <span class="favorite-mark" :class="{ active: item.favorite }">{{ item.favorite ? '★' : '☆' }}</span>
        </button>
        <div v-if="!recommendations.length" class="empty-state compact">当前筛选没有真实推荐结果。</div>
        <el-button v-if="hasMore" text class="load-more" @click="load(false)">加载更多</el-button>
      </aside>

      <main class="surface recommendation-detail">
        <template v-if="detail">
          <header><div><span class="section-kicker">推荐依据</span><h2>{{ detail.recommendation.job.title }}</h2><p>{{ detail.recommendation.job.companyName }} · {{ detail.recommendation.job.city || '地点未披露' }}</p></div><div class="recommendation-actions"><el-button type="primary" size="small" @click="$router.push(`/ai-studio?jobId=${detail.recommendation.job.id}`)">优化简历与草稿</el-button><el-button size="small" :loading="actionLoading" @click="addToQueue(detail.recommendation)">加入投递队列</el-button><el-button size="small" :loading="actionLoading" @click="toggleFavorite(detail.recommendation)">{{ detail.recommendation.favorite ? '取消收藏' : '收藏' }}</el-button><el-button v-if="detail.recommendation.status !== 'IGNORED'" size="small" @click="ignoreItem(detail.recommendation)">忽略</el-button><el-button v-else size="small" @click="restoreItem(detail.recommendation)">恢复</el-button></div></header>
          <div class="recommendation-facts"><div><span>薪资</span><strong>{{ salary(detail.recommendation.job) }}</strong></div><div><span>来源</span><strong>{{ displayText(detail.recommendation.job.sourcePlatform) }}</strong></div><div><span>发布时间</span><strong>{{ time(detail.recommendation.job.publishAt) }}</strong></div></div>
          <section><h3>排序依据</h3><p>{{ displayText(detail.recommendation.rankBasis.source) }} · {{ detail.recommendation.rankBasis.version }}</p><small>使用最新匹配的推荐结论、综合分、发布时间和稳定 ID；没有隐藏的模型加分。</small></section>
          <section><h3>岗位技能</h3><div class="tag-row"><span v-for="skill in detail.recommendation.job.skills" :key="skill.id" class="plain-tag" :class="{ 'accent-tag': skill.requirementType === 'MUST_HAVE' }">{{ skill.name }} · {{ displayText(skill.requirementType) }}</span></div></section>
          <section v-if="detail.events.length"><h3>状态事件</h3><div v-for="event in detail.events" :key="event.id" class="recommendation-event"><strong>{{ displayText(event.eventType) }}</strong><span>{{ event.reason || '状态更新' }}</span><time>{{ time(event.occurredAt) }}</time></div></section>
          <div v-if="detail.recommendation.status === 'UNEVALUATED'" class="state-notice">该岗位尚未评估，分数保持为空，不显示假 0 分。</div>
          <div v-if="detail.recommendation.status === 'MATCH_FAILED'" class="state-notice danger">最近匹配失败。岗位事实仍安全保留，可在右侧重试。</div>
        </template>
        <div v-else class="empty-state centered">选择推荐后查看排序事实和状态事件。</div>
      </main>

      <MatchAnalysisPanel :enabled="Boolean(detail)" :current-match="detail?.matchAnalysis" :active-run="activeRun"
        :history="history" :loading="actionLoading" compact @run="runMatch" @retry="retryMatch" @view-history="viewHistory" />
    </div>

    <div v-if="capabilities?.applicationTracking" class="capability-boundary">
      <strong>申请队列已启用</strong><span>加入队列不会自动提交；请在申请中心完成人工确认。</span>
    </div>
  </section>
</template>
