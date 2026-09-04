<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '../../utils/elementFeedback.js'
import { getOperationsOverview, updateAiBudget } from '../../api/operations.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText, displayEnabled } from '../../utils/displayText.js'

const loading=ref(true),saving=ref(false),overview=ref(null)
const budget=reactive({version:0,dailyTokenLimit:null,monthlyTokenLimit:null,dailyCostLimit:null,monthlyCostLimit:null,currency:'USD',warningThresholdPercent:80,enabled:false})
const usage=computed(()=>overview.value?.aiUsage)
const runs=computed(()=>overview.value?.recentRuns||[])
const healthEntries=computed(()=>Object.entries(overview.value?.health||{}))
const safetyEntries=computed(()=>Object.entries(overview.value?.safety||{}))

onMounted(load)
async function load(){loading.value=true;try{const {data}=await getOperationsOverview();overview.value=data.data;Object.assign(budget,{...data.data.aiUsage.budget})}catch(e){ElMessage.error(apiErrorMessage(e,'运维数据加载失败'))}finally{loading.value=false}}
async function saveBudget(){saving.value=true;try{const payload={version:budget.version,dailyTokenLimit:numberOrNull(budget.dailyTokenLimit),monthlyTokenLimit:numberOrNull(budget.monthlyTokenLimit),dailyCostLimit:numberOrNull(budget.dailyCostLimit),monthlyCostLimit:numberOrNull(budget.monthlyCostLimit),currency:budget.currency,warningThresholdPercent:Number(budget.warningThresholdPercent),enabled:budget.enabled};await updateAiBudget(payload);ElMessage.success('AI 预算已保存');await load()}catch(e){ElMessage.error(apiErrorMessage(e,'预算保存失败'))}finally{saving.value=false}}
function numberOrNull(value){return value===null||value===''?null:Number(value)}
function state(value){return String(value||'unknown').toLowerCase()}
function metric(value){return value===null||value===undefined?'—':value}
</script>

<template>
  <section v-loading="loading" class="page-section operations-center">
    <div class="page-heading"><div><p class="eyebrow accent">阶段 11 · 运营就绪度</p><h1>系统运营</h1><p>真实运行健康、可观测性、AI 成本边界、备份恢复、安全扫描与性能基线。</p></div><el-button @click="load">刷新状态</el-button></div>

    <template v-if="overview">
      <div class="ops-health-strip">
        <article v-for="[name,value] in healthEntries" :key="name"><span>{{displayText(name)}}</span><strong :class="state(value)"><i/>{{displayText(value)}}</strong></article>
      </div>

      <div class="ops-grid top">
        <section class="surface ops-observability"><div class="section-heading"><div><span class="section-kicker">可观测性</span><h2>信号与导出边界</h2></div></div>
          <dl><div><dt>链路标识透传</dt><dd>{{displayEnabled(overview.observability.tracePropagation)}}</dd></div><div><dt>当前链路标识</dt><dd class="mono">{{overview.observability.traceId}}</dd></div><div><dt>Prometheus</dt><dd>{{displayEnabled(overview.observability.prometheusEnabled)}}</dd></div><div><dt>OTLP 导出器</dt><dd>{{overview.observability.otlpEnabled?'已启用':'默认关闭'}}</dd></div><div><dt>结构化日志</dt><dd>{{overview.observability.structuredLogging?'ECS JSON':'开发文本'}}</dd></div></dl>
        </section>
        <section class="surface ops-provider"><div class="section-heading"><div><span class="section-kicker">服务商运行状态</span><h2>AI 与熔断器</h2></div><span :class="['status-mark',state(usage.providerStatus.serviceStatus)]">{{displayText(usage.providerStatus.serviceStatus)}}</span></div>
          <dl><div><dt>解析模式</dt><dd>{{displayText(usage.providerStatus.parserMode)}}</dd></div><div><dt>LLM</dt><dd>{{usage.providerStatus.llmConfigured?'已配置':'未配置'}}</dd></div><div><dt>熔断器</dt><dd>{{displayText(usage.providerStatus.circuitBreaker)}}</dd></div><div><dt>向量服务商</dt><dd>{{usage.providerStatus.embeddingProvider}}</dd></div><div><dt>模型</dt><dd>{{usage.providerStatus.embeddingModel}}</dd></div></dl>
        </section>
      </div>

      <div class="ops-grid middle">
        <section class="surface ops-usage"><div class="section-heading"><div><span class="section-kicker">真实 AI 调用日志</span><h2>令牌与成本</h2></div><span :class="['status-mark',state(usage.budget.status)]">{{displayText(usage.budget.status)}}</span></div>
          <div class="ops-kpis"><article><span>今日调用</span><strong>{{usage.callsToday}}</strong></article><article><span>本月调用</span><strong>{{usage.callsMonth}}</strong></article><article><span>今日令牌</span><strong>{{usage.inputTokensToday+usage.outputTokensToday}}</strong></article><article><span>本月令牌</span><strong>{{usage.inputTokensMonth+usage.outputTokensMonth}}</strong></article></div>
          <div class="ops-provider-row" v-for="item in usage.providers" :key="`${item.provider}-${item.model}`"><strong>{{displayText(item.provider)}}</strong><span>{{displayText(item.model)}}</span><span>{{item.calls}} 次调用</span><span>{{item.inputTokens+item.outputTokens}} 个令牌</span><em>{{item.failures}} 次失败</em></div>
          <div v-if="!usage.providers.length" class="empty-state compact">本月没有 AI 调用事实。</div>
        </section>
        <section class="surface ops-budget"><div class="section-heading"><div><span class="section-kicker">明确限制</span><h2>AI 预算</h2></div><el-switch v-model="budget.enabled"/></div>
          <div class="ops-budget-grid"><label>每日令牌上限<el-input-number v-model="budget.dailyTokenLimit" :min="1" controls-position="right"/></label><label>每月令牌上限<el-input-number v-model="budget.monthlyTokenLimit" :min="1" controls-position="right"/></label><label>每日成本上限<el-input-number v-model="budget.dailyCostLimit" :min="0.000001" :precision="6" controls-position="right"/></label><label>每月成本上限<el-input-number v-model="budget.monthlyCostLimit" :min="0.000001" :precision="6" controls-position="right"/></label><label>币种<el-input v-model="budget.currency" maxlength="3"/></label><label>告警阈值 %<el-input-number v-model="budget.warningThresholdPercent" :min="1" :max="100"/></label></div>
          <p>不同币种不会自动换算；未配置 LLM 时不会伪造成本。</p><el-button type="primary" :loading="saving" @click="saveBudget">保存预算</el-button>
        </section>
      </div>

      <section class="surface ops-runs"><div class="section-heading"><div><span class="section-kicker">不可变证据</span><h2>运行记录</h2></div><strong>{{runs.length}}</strong></div>
        <div class="ops-run-head"><span>类型 / 批次</span><span>状态</span><span>指标</span><span>RPO / RTO</span><span>完成时间</span></div>
        <article v-for="run in runs" :key="run.id"><div><strong>{{displayText(run.runType)}}</strong><span>{{run.batchId}}</span></div><span :class="['status-mark',state(run.status)]">{{displayText(run.status)}}</span><small>{{run.summary}}</small><span>{{metric(run.rpoSeconds)}} 秒 / {{metric(run.rtoSeconds)}} 秒</span><time>{{run.finishedAt?new Date(run.finishedAt).toLocaleString('zh-CN'):'运行中'}}</time></article>
        <div v-if="!runs.length" class="empty-state compact">尚未执行备份、恢复、扫描或负载测试。</div>
      </section>

      <section class="surface ops-safety"><div><span class="section-kicker">不可突破的边界</span><h2>安全边界</h2><p>运维能力不会获得招聘平台写权限，也不提供覆盖生产数据的一键恢复。</p></div><div><span v-for="[name,value] in safetyEntries" :key="name"><b>{{displayText(name)}}</b><strong>{{displayText(value)}}</strong></span></div></section>
    </template>
  </section>
</template>
