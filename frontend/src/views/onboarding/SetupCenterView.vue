<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from '../../utils/elementFeedback.js'
import { getDataQuality, getOnboardingOverview } from '../../api/onboarding.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'

const loading = ref(true)
const overview = ref(null)
const quality = ref(null)

const scoreTone = computed(() => overview.value?.readyForMatching ? 'ready' : 'needs-work')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [overviewResponse, qualityResponse] = await Promise.all([
      getOnboardingOverview(),
      getDataQuality()
    ])
    overview.value = overviewResponse.data.data
    quality.value = qualityResponse.data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '初始化中心加载失败'))
  } finally {
    loading.value = false
  }
}

function time(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}
</script>

<template>
  <section v-loading="loading" class="page-section setup-center">
    <div class="page-heading">
      <div>
        <p class="eyebrow accent">阶段 13 · 引导式初始化</p>
        <h1>首次使用与数据质量</h1>
        <p>只根据当前账户的真实资料与简历计算；不会自动补写、猜测或修改候选人事实。</p>
      </div>
      <button class="secondary-button" :disabled="loading" @click="load">刷新检查</button>
    </div>

    <template v-if="overview && quality">
      <section class="setup-hero" :class="scoreTone">
        <div class="setup-score">
          <span>就绪度</span>
          <strong>{{ overview.score }}</strong>
          <small>/ 100</small>
        </div>
        <div class="setup-hero-copy">
          <div class="setup-status"><i />{{ overview.status === 'READY' ? '已就绪 · 可以开始稳定匹配' : '需要完善 · 建议先补充关键事实' }}</div>
          <h2>{{ overview.completedSteps }} / {{ overview.totalSteps }} 项已完成</h2>
          <p>就绪门槛为 80 分且不存在阻塞项。评分是确定性检查，不代表岗位录用概率。</p>
        </div>
        <div class="setup-quality-summary">
          <div><span>阻塞项</span><strong>{{ quality.summary.blockers }}</strong></div>
          <div><span>警告</span><strong>{{ quality.summary.warnings }}</strong></div>
          <div><span>提示</span><strong>{{ quality.summary.info }}</strong></div>
        </div>
      </section>

      <div class="setup-layout">
        <section class="setup-checklist">
          <div class="section-heading">
            <div><span class="section-kicker">真实数据检查清单</span><h2>准备清单</h2></div>
            <small>{{ time(overview.generatedAt) }}</small>
          </div>
          <article v-for="(step, index) in overview.steps" :key="step.key" class="setup-step">
            <div class="setup-step-index" :class="step.status.toLowerCase()">{{ String(index + 1).padStart(2, '0') }}</div>
            <div class="setup-step-copy">
              <div><strong>{{ step.title }}</strong><span :class="step.status.toLowerCase()">{{ displayText(step.status) }}</span></div>
              <p>{{ step.description }}</p>
              <div class="setup-progress"><i :style="{ width: `${Math.round(step.earnedScore / step.weight * 100)}%` }" /></div>
              <small>当前 {{ step.currentValue }} / 目标 {{ step.targetValue }} · 得分 {{ step.earnedScore }} / {{ step.weight }}<template v-if="step.blocking"> · 关键项</template></small>
            </div>
            <router-link :to="step.actionPath" class="text-action">完善 →</router-link>
          </article>
        </section>

        <aside class="setup-quality">
          <div class="section-heading">
            <div><span class="section-kicker">数据质量</span><h2>确定性问题</h2></div>
            <strong>{{ quality.summary.total }}</strong>
          </div>
          <div v-if="quality.issues.length" class="quality-list">
            <article v-for="issue in quality.issues" :key="issue.code" :class="issue.severity.toLowerCase()">
              <div><span>{{ displayText(issue.severity) }}</span><code>{{ displayText(issue.code) }}</code></div>
              <strong>{{ issue.title }}</strong>
              <p>{{ issue.description }}</p>
              <router-link :to="issue.actionPath">前往处理 →</router-link>
            </article>
          </div>
          <div v-else class="quality-clear">
            <i>✓</i>
            <strong>当前未发现确定性数据质量问题</strong>
            <p>检查结果来自 MySQL 中的当前用户事实，不代表系统替你确认了内容真实性。</p>
          </div>
          <div class="setup-boundary">
            <strong>事实边界</strong>
            <p>JobPilot 不会因为检查项缺失而自动生成经历、技能或简历内容。所有修改仍需你在候选人档案或简历中心明确保存。</p>
          </div>
        </aside>
      </div>
    </template>

    <div v-else-if="!loading" class="empty-state">无法读取初始化数据，请检查后端服务后重试。</div>
  </section>
</template>
