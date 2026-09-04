<script setup>
import { computed } from 'vue'
import { displayGeneratedText, displayText } from '../../utils/displayText.js'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  currentMatch: { type: Object, default: null },
  activeRun: { type: Object, default: null },
  history: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

defineEmits(['run', 'retry', 'view-history'])

const dimensions = computed(() => props.currentMatch ? [
  ['技能', props.currentMatch.scores.skill, props.currentMatch.effectiveWeights.skill],
  ['向量语义', props.currentMatch.scores.embedding, props.currentMatch.effectiveWeights.embedding],
  ['LLM', props.currentMatch.scores.llm, props.currentMatch.effectiveWeights.llm],
  ['项目', props.currentMatch.scores.project, props.currentMatch.effectiveWeights.project],
  ['求职偏好', props.currentMatch.scores.preference, props.currentMatch.effectiveWeights.preference],
  ['公司', props.currentMatch.scores.company, props.currentMatch.effectiveWeights.company]
] : [])

function score(value) { return value == null ? '—' : Number(value).toFixed(1) }
function percent(value) { return value == null ? 0 : Math.max(0, Math.min(100, Number(value))) }
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
</script>

<template>
  <aside class="surface match-analysis-panel" :class="{ compact }">
    <div class="match-panel-heading">
      <div><span class="section-kicker">匹配分析</span><h2>匹配分析</h2></div>
      <el-button type="primary" size="small" :loading="loading" :disabled="!enabled" @click="$emit('run', Boolean(currentMatch))">
        {{ currentMatch ? '重新评估' : '开始匹配' }}
      </el-button>
    </div>

    <div v-if="activeRun && !currentMatch" class="match-run-state">
      <strong>{{ displayText(activeRun.status) }}</strong>
      <span>尝试 {{ activeRun.attemptCount }} / {{ activeRun.maxAttempts }}</span>
      <p v-if="activeRun.errorMessage">{{ displayGeneratedText(activeRun.errorMessage) }}</p>
      <el-button v-if="['FAILED', 'DEAD'].includes(activeRun.status)" size="small" @click="$emit('retry')">安全重试</el-button>
    </div>

    <template v-if="currentMatch">
      <div class="match-score-hero">
        <div class="score-orbit"><strong>{{ score(currentMatch.scores.overall) }}</strong><span>/ 100</span></div>
        <div>
          <i :class="`level-${(currentMatch.level || 'd').toLowerCase()}`">{{ currentMatch.level || '—' }}</i>
          <strong>{{ displayText(currentMatch.recommendation) }}</strong>
          <span>{{ displayText(currentMatch.hardFilterResult) }} · 扣分 {{ score(currentMatch.scores.penalty) }}</span>
        </div>
      </div>

      <div class="llm-mode-note" :class="{ skipped: currentMatch.llmStatus === 'SKIPPED_NOT_CONFIGURED' }">
        <strong>LLM：{{ displayText(currentMatch.llmStatus) }}</strong>
        <span v-if="currentMatch.llmStatus === 'SKIPPED_NOT_CONFIGURED'">未配置，已跳过；其余有效权重已重新归一化。</span>
        <span v-else>{{ currentMatch.modelName || '服务商模型' }} · {{ currentMatch.promptVersion }}</span>
      </div>

      <section class="match-dimensions">
        <div v-for="dimension in dimensions" :key="dimension[0]">
          <header><span>{{ dimension[0] }}</span><strong>{{ score(dimension[1]) }}</strong><small>{{ score(dimension[2]) }}%</small></header>
          <div><i :style="{ width: `${percent(dimension[1])}%` }" /></div>
        </div>
      </section>

      <section class="analysis-list advantages"><h3>优势</h3><p v-for="item in currentMatch.advantages" :key="item.text"><strong>+</strong><span>{{ displayGeneratedText(item.text) }}<small v-if="item.candidateEvidenceRefs?.length">{{ item.candidateEvidenceRefs.join(' · ') }}</small></span></p><em v-if="!currentMatch.advantages.length">暂无有证据的优势。</em></section>
      <section class="analysis-list gaps"><h3>缺口</h3><p v-for="item in currentMatch.gaps" :key="item.text"><strong>–</strong><span>{{ displayGeneratedText(item.text) }}<small>{{ item.jobEvidence }}</small></span></p><em v-if="!currentMatch.gaps.length">未发现结构化缺口。</em></section>
      <section class="analysis-list risks"><h3>风险</h3><p v-for="item in currentMatch.risks" :key="item.text"><strong>!</strong><span>{{ displayGeneratedText(item.text) }}<small>{{ displayText(item.severity) }}</small></span></p><em v-if="!currentMatch.risks.length">无额外风险。</em></section>

      <blockquote class="match-reason">{{ displayGeneratedText(currentMatch.reason) }}</blockquote>
      <dl class="match-meta">
        <div><dt>算法版本</dt><dd>{{ currentMatch.algorithmVersion }}</dd></div>
        <div><dt>配置版本</dt><dd>v{{ currentMatch.configVersion }}</dd></div>
        <div><dt>向量模型</dt><dd>{{ currentMatch.embeddingModel || '—' }}</dd></div>
        <div><dt>向量版本</dt><dd>{{ currentMatch.embeddingVersion || '—' }}</dd></div>
        <div><dt>评估时间</dt><dd>{{ time(currentMatch.evaluatedAt) }}</dd></div>
      </dl>

      <div v-if="history.length > 1" class="match-history">
        <h3>历史版本</h3>
        <button v-for="item in history" :key="item.id" :class="{ active: item.id === currentMatch.id }" @click="$emit('view-history', item.id)">
          <span>{{ displayText(item.level || item.hardFilterResult) }} · {{ score(item.scores.overall) }}</span><time>{{ time(item.evaluatedAt) }}</time>
        </button>
      </div>
    </template>
    <div v-else-if="!activeRun" class="empty-state compact">尚未评估。匹配会使用候选人档案、默认简历、真实岗位技能和 BGE-M3 向量。</div>
  </aside>
</template>
