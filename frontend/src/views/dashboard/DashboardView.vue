<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from '../../utils/elementFeedback.js'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { getAnalyticsDashboard } from '../../api/analytics.js'
import { getInterviewDashboard } from '../../api/interviews.js'
import { getOnboardingOverview } from '../../api/onboarding.js'
import { getOfferDashboard } from '../../api/offers.js'
import { apiErrorMessage } from '../../api/http.js'
import { useSettingsStore } from '../../stores/settings.js'
import { displayText } from '../../utils/displayText.js'

const settings = useSettingsStore()

const loading = ref(true)
const dashboard = ref(null)
const interviewDashboard = ref(null)
const offerDashboard = ref(null)
const onboarding = ref(null)
const levelElement = ref(null)
const sourceElement = ref(null)
let levelChart
let sourceChart

use([BarChart, PieChart, GridComponent, TooltipComponent, CanvasRenderer])

const metrics = computed(() => {
  if (!dashboard.value) return []
  const data = dashboard.value.matching
  const applications = dashboard.value.applications
  return [
    { label: '活动队列', value: applications.queueActive, tone: 'accent' },
    { label: '需要处理', value: applications.queueNeedReview },
    { label: '真实投递', value: applications.applications },
    { label: '收到回复', value: applications.replied },
    { label: 'S / A 高匹配', value: data.highMatchJobs },
    { label: '档案完整度', value: `${dashboard.value.foundation.profileCompleteness}%` }
  ]
})

onMounted(load)
onBeforeUnmount(() => {
  levelChart?.dispose()
  sourceChart?.dispose()
  window.removeEventListener('resize', resizeCharts)
})

async function load() {
  loading.value = true
  try {
    const [analyticsResponse, interviewResponse, offerResponse, onboardingResponse] = await Promise.all([getAnalyticsDashboard(), getInterviewDashboard(), getOfferDashboard(), getOnboardingOverview()])
    dashboard.value = analyticsResponse.data.data
    interviewDashboard.value = interviewResponse.data.data
    offerDashboard.value = offerResponse.data.data
    onboarding.value = onboardingResponse.data.data
    await nextTick()
    renderCharts()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '工作台加载失败'))
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  levelChart?.dispose()
  sourceChart?.dispose()
  levelChart = init(levelElement.value)
  sourceChart = init(sourceElement.value)
  const levels = dashboard.value.levels.filter((item) => item.value > 0)
  levelChart.setOption({
    tooltip: { trigger: 'item' },
    color: ['#246b54', '#4d8d73', '#85a892', '#c8a15d', '#b66a61', '#88928e'],
    series: [{ type: 'pie', radius: ['57%', '78%'], label: { color: '#66736e', fontSize: 10 }, data: levels.map((item) => ({ name: displayText(item.label), value: item.value })) }]
  })
  sourceChart.setOption({
    grid: { left: 20, right: 15, top: 8, bottom: 22, containLabel: true },
    xAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#edf0ed' } } },
    yAxis: { type: 'category', data: dashboard.value.sources.map((item) => displayText(item.label)), axisTick: { show: false }, axisLine: { show: false } },
    tooltip: { trigger: 'axis' },
    series: [{ type: 'bar', data: dashboard.value.sources.map((item) => item.value), barWidth: 13, itemStyle: { color: '#3f7f67', borderRadius: 1 } }]
  })
  window.removeEventListener('resize', resizeCharts)
  window.addEventListener('resize', resizeCharts)
}

function resizeCharts() { levelChart?.resize(); sourceChart?.resize() }
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
function score(value) { return value == null ? '—' : Number(value).toFixed(1) }
function salary(job) { return job.salaryText ? String(job.salaryText).replace(/^MANUAL\s+/i, '手动录入 ') : (job.salaryMin != null ? `${Number(job.salaryMin / 1000).toFixed(0)}–${Number(job.salaryMax / 1000).toFixed(0)}K` : '薪资未披露') }
</script>

<template>
  <section v-loading="loading" class="page-section phase4-dashboard">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 9 · OFFER 决策</p><h1>求职工作台</h1><p>岗位、投递、面试与 Offer 指标全部来自真实数据库；数据分析会明确展示所有统计口径。</p></div>
      <router-link to="/offers" class="text-action">打开 Offer 中心 →</router-link>
    </div>

    <router-link v-if="onboarding && settings.showDashboardBanner" to="/setup" class="dashboard-setup-strip" :class="{ ready: onboarding.readyForMatching }">
      <div><span>初始化就绪度</span><strong>{{ onboarding.score }} / 100 · {{ displayText(onboarding.status) }}</strong></div>
      <p>{{ onboarding.completedSteps }}/{{ onboarding.totalSteps }} 项完成 · {{ onboarding.qualitySummary.blockers }} 个阻塞项 · {{ onboarding.qualitySummary.warnings }} 个警告</p>
      <b>查看数据质量 →</b>
    </router-link>

    <div class="metrics-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric" :class="metric.tone"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong></div>
    </div>

    <template v-if="dashboard">
      <section v-if="interviewDashboard" class="surface dashboard-interviews">
        <div class="section-heading"><div><span class="section-kicker">面试管理</span><h2>近期面试与提醒</h2></div><router-link to="/interviews" class="inline-link">管理面试 →</router-link></div>
        <div class="dashboard-interview-summary"><div><span>近期面试</span><strong>{{ interviewDashboard.upcomingInterviews.length }}</strong></div><div><span>待办提醒</span><strong>{{ interviewDashboard.pendingReminders.length }}</strong></div><div><span>待确认复盘</span><strong>{{ interviewDashboard.reviewPendingCount }}</strong></div><div><span>活动知识缺口</span><strong>{{ interviewDashboard.activeKnowledgeGapCount }}</strong></div></div>
        <div class="dashboard-interview-rows"><router-link v-for="item in interviewDashboard.upcomingInterviews.slice(0, 4)" :key="item.id" to="/interviews"><div><strong>{{ item.companyName }} · {{ item.role }}</strong><span>{{ item.rounds?.[0]?.title || '轮次待安排' }}</span></div><time>{{ time(item.rounds?.[0]?.scheduledStartAt) }}</time></router-link><div v-if="!interviewDashboard.upcomingInterviews.length" class="empty-state compact">暂无近期面试。</div></div>
      </section>

      <section v-if="offerDashboard" class="surface dashboard-offers">
        <div class="section-heading"><div><span class="section-kicker">OFFER 决策</span><h2>Offer 与截止事项</h2></div><router-link to="/offers" class="inline-link">管理 Offer →</router-link></div>
        <div class="offer-dashboard-kpis"><div><span>进行中</span><strong>{{offerDashboard.activeOffers}}</strong></div><div><span>已接受</span><strong>{{offerDashboard.acceptedOffers}}</strong></div><div><span>待办截止</span><strong>{{offerDashboard.pendingDeadlines.length}}</strong></div></div>
        <router-link v-for="item in offerDashboard.pendingDeadlines.slice(0,3)" :key="item.id" to="/offers" class="offer-dashboard-deadline"><strong>{{item.companyName}} · {{item.title}}</strong><time>{{time(item.dueAt)}}</time></router-link><div v-if="!offerDashboard.pendingDeadlines.length" class="empty-state compact">暂无待处理 Offer 截止事项。</div>
      </section>

      <div class="dashboard-recommendation-grid">
        <section class="surface today-recommendations">
          <div class="section-heading"><div><span class="section-kicker">今日优先事项</span><h2>今日推荐</h2></div><strong>{{ dashboard.todayRecommendations.length }}</strong></div>
          <router-link v-for="item in dashboard.todayRecommendations" :key="item.id" :to="`/recommendations?jobId=${item.job.id}`" class="today-job-row">
            <i :class="`level-${(item.match?.level || 'na').toLowerCase()}`">{{ item.match?.level || '—' }}</i>
            <div><strong>{{ item.job.title }}</strong><span>{{ item.job.companyName }} · {{ item.job.city || '地点未披露' }} · {{ salary(item.job) }}</span><small>{{ displayText(item.match?.recommendation || item.status) }}<template v-if="item.favorite"> · 已收藏</template> · {{ time(item.match?.evaluatedAt) }}</small></div>
            <b>{{ score(item.match?.overallScore) }}</b>
          </router-link>
          <div v-if="!dashboard.todayRecommendations.length" class="empty-state compact">暂无已评估推荐。前往 AI 岗位推荐刷新真实匹配。</div>
        </section>
        <section class="surface dashboard-chart"><div class="section-heading"><div><span class="section-kicker">匹配等级</span><h2>等级分布</h2></div></div><div ref="levelElement" class="chart-canvas" /></section>
        <section class="surface dashboard-chart"><div class="section-heading"><div><span class="section-kicker">岗位来源</span><h2>来源分布</h2></div></div><div ref="sourceElement" class="chart-canvas" /></section>
      </div>

      <section class="surface dashboard-funnel">
        <div class="section-heading"><div><span class="section-kicker">申请转化漏斗</span><h2>推荐 → 投递漏斗</h2></div><router-link to="/analytics" class="inline-link">完整数据分析 →</router-link></div>
        <div><article v-for="(stage, index) in dashboard.funnel" :key="stage.key"><small>0{{ index + 1 }}</small><strong>{{ stage.value }}</strong><span>{{ displayText(stage.label) }}</span><em>分子 {{ stage.numerator }} / 分母 {{ stage.denominator }}</em></article></div>
      </section>

      <div class="dashboard-foundation-grid">
        <section class="surface profile-progress"><div class="section-heading"><div><span class="section-kicker">候选人就绪度</span><h2>候选人资料</h2></div><strong>{{ dashboard.foundation.profileCompleteness }}%</strong></div><el-progress :percentage="dashboard.foundation.profileCompleteness" :stroke-width="8" :show-text="false" /><p class="muted">教育 {{ dashboard.foundation.educationCount }} · 经历 {{ dashboard.foundation.experiencesCount }} · 项目 {{ dashboard.foundation.projectsCount }} · 技能 {{ dashboard.foundation.skillsCount }}</p><router-link to="/candidate" class="inline-link">编辑候选人档案</router-link></section>
        <section class="surface resume-focus"><div class="section-heading"><div><span class="section-kicker">简历事实来源</span><h2>默认简历</h2></div></div><template v-if="dashboard.foundation.defaultResume"><div class="resume-line"><strong>{{ dashboard.foundation.defaultResume.name }}</strong><span>v{{ dashboard.foundation.defaultResume.currentVersionNumber || '—' }}</span></div><p>{{ dashboard.foundation.defaultResume.targetRole || '未设置目标岗位' }}</p><div class="tag-row"><span v-if="dashboard.foundation.defaultResume.master" class="plain-tag">主简历</span><span class="plain-tag accent-tag">默认</span></div></template><div v-else class="empty-inline">尚未创建简历。</div><router-link to="/resumes" class="inline-link">打开简历中心</router-link></section>
        <section class="surface unavailable-data"><span class="section-kicker">能力边界</span><h2>安全与阶段边界</h2><p>Offer 与数据分析已开放；系统不会自动接受 Offer、虚构汇率、录音、发送外部消息或自动提交申请。</p><div v-for="item in dashboard.unavailableCapabilities.slice(0, 5)" :key="item.key"><span>{{ displayText(item.key) }}</span><small>{{ displayText(item.phase) }}</small></div><router-link to="/analytics" class="inline-link">查看统计口径</router-link></section>
      </div>
      <p class="last-updated">生成时间：{{ time(dashboard.generatedAt) }} · 时区 {{ dashboard.timezone }} · 忽略 {{ dashboard.matching.ignoredJobs }} · 匹配失败 {{ dashboard.matching.matchFailedJobs }}</p>
    </template>
  </section>
</template>
