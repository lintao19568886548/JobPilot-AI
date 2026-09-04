<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import { activateModel, getLearningDashboard, listModels, rebuildFeedback, rollbackModel, shadowModel, trainModel } from '../../api/learning.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'

const loading = ref(true), working = ref(false), dashboard = ref(null), models = ref([])
const range = ref(defaultRange()), modelName = ref('保守学习模型')
const weights = computed(() => dashboard.value?.activeModel?.parameters?.weights || {})

onMounted(load)
async function load() { loading.value = true; try { const [d,m] = await Promise.all([getLearningDashboard(), listModels()]); dashboard.value=d.data.data; models.value=m.data.data } catch(e) { ElMessage.error(apiErrorMessage(e,'学习数据加载失败')) } finally { loading.value=false } }
async function rebuild() { await act(async()=>{const r=await rebuildFeedback({from:range.value[0],to:range.value[1]},`feedback-ui-${crypto.randomUUID()}`);ElMessage.success(`反馈已重建：新增 ${r.data.data.createdCount} 条`);await load()}) }
async function train() { await act(async()=>{const r=await trainModel({from:range.value[0],to:range.value[1],name:modelName.value},`train-ui-${crypto.randomUUID()}`);ElMessage.success(r.data.data.model?'模型训练与留出评估完成':'样本不足，未创建模型');await load()}) }
async function shadow(model) { await act(async()=>{await shadowModel(model.id,`shadow-ui-${crypto.randomUUID()}`);ElMessage.success('影子排序完成，线上顺序未改变');await load()}) }
async function activate(model) { try { await ElMessageBox.confirm('仅切换 JobPilot 内部排序，不触发投递或外部操作。确认启用该模型？','显式启用',{type:'warning'}); await act(async()=>{await activateModel(model);ElMessage.success('模型已启用');await load()}) } catch(e) { if(e!=='cancel'&&e!=='close') ElMessage.error(apiErrorMessage(e,'启用失败')) } }
async function rollback(model) { try { await ElMessageBox.confirm('将立即恢复上一个模型；若不存在则恢复基线排序。','回滚模型',{type:'warning'}); await act(async()=>{await rollbackModel(model);ElMessage.success('排序已回滚');await load()}) } catch(e) { if(e!=='cancel'&&e!=='close') ElMessage.error(apiErrorMessage(e,'回滚失败')) } }
async function act(fn){working.value=true;try{await fn()}catch(e){ElMessage.error(apiErrorMessage(e,'操作失败'))}finally{working.value=false}}
function defaultRange(){const to=new Date(),from=new Date();from.setDate(to.getDate()-365);return [iso(from),iso(to)]}function iso(v){return v.toISOString().slice(0,10)}
function metric(model,key){const value=model?.evaluation?.[key];return value==null?'—':Number(value).toFixed(4)}
</script>

<template>
  <section v-loading="loading" class="page-section phase10-center">
    <div class="page-heading"><div><p class="eyebrow accent">阶段 10 · 反馈学习</p><h1>学习中心</h1><p>只用结果发生前的历史特征学习；时间留出、影子比较、显式启用与一键回滚。</p></div><div class="heading-actions"><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD"/><el-button :loading="working" @click="rebuild">重建反馈</el-button><el-button type="primary" :loading="working" :disabled="!dashboard?.feedback?.enoughForTraining" @click="train">训练候选模型</el-button></div></div>
    <template v-if="dashboard">
      <div class="metrics-grid phase10-metrics"><article class="metric accent"><span>不可变反馈</span><strong>{{dashboard.feedback.total}}</strong></article><article class="metric"><span>最低样本</span><strong>{{dashboard.feedback.minimumSample}}</strong></article><article class="metric"><span>模型版本</span><strong>{{dashboard.modelCount}}</strong></article><article class="metric"><span>影子结果</span><strong>{{dashboard.shadowCount}}</strong></article><article class="metric"><span>当前排序</span><strong class="metric-text">{{dashboard.mode}}</strong></article></div>
      <div class="phase10-grid">
        <section class="surface phase10-panel"><div class="section-heading"><div><span class="section-kicker">不可变结果</span><h2>反馈事实</h2></div><span :class="['status-mark',dashboard.feedback.enoughForTraining?'safe':'muted']">{{dashboard.feedback.enoughForTraining?'样本可训练':'等待样本'}}</span></div><div class="outcome-list"><div v-for="(count,key) in dashboard.feedback.byEvent" :key="key"><span>{{displayText(key)}}</span><strong>{{count}}</strong></div></div><p class="boundary-copy">查看、投递、回复、面试和 Offer 结果只追加；特征快照固定到结果发生前最近一次匹配。</p></section>
        <section class="surface phase10-panel"><div class="section-heading"><div><span class="section-kicker">当前参数</span><h2>当前模型</h2></div><strong>{{dashboard.activeModel?.name||'稳定基线'}}</strong></div><div v-if="dashboard.activeModel" class="weight-list"><div v-for="(value,key) in weights" :key="key"><span>{{displayText(key)}}</span><div><i :style="{width:`${value}%`}"/></div><b>{{value}}%</b></div></div><p v-else class="empty-state compact">尚未显式启用学习模型，当前使用稳定基线。</p></section>
        <section class="surface phase10-panel boundary-panel"><span class="section-kicker">安全边界</span><h2>不会自行行动</h2><p v-for="item in dashboard.safetyBoundaries" :key="item">{{item}}</p></section>
      </div>
      <section class="surface model-history"><header><div><span class="section-kicker">版本化模型</span><h2>训练与影子历史</h2></div><el-input v-model="modelName" maxlength="160" placeholder="新模型名称"/></header><div class="model-head"><span>版本</span><span>样本 / 状态</span><span>NDCG@10 基线 → 候选</span><span>影子变化</span><span>操作</span></div><article v-for="model in models" :key="model.id" class="model-row"><div><strong>v{{model.versionNo}} · {{model.name}}</strong><small>{{new Date(model.createdAt).toLocaleString('zh-CN')}}</small></div><div><span>{{model.sampleCount}} 个样本</span><small>{{displayText(model.status)}} · {{model.activationEligible?'可启用':'不可启用'}}</small></div><div><span>{{metric(model,'baselineNdcgAt10')}} → {{metric(model,'candidateNdcgAt10')}}</span><small>按时间顺序 80/20 切分</small></div><div><span>{{model.shadowResults.length}} 行</span><small>线上排序未改变</small></div><div class="row-actions"><el-button v-if="model.status==='DRAFT'" size="small" :disabled="!model.activationEligible" @click="shadow(model)">影子运行</el-button><el-button v-if="model.status==='SHADOW'" size="small" type="primary" @click="activate(model)">显式启用</el-button><el-button v-if="model.status==='ACTIVE'" size="small" @click="rollback(model)">回滚</el-button></div></article><div v-if="!models.length" class="empty-state">尚无模型；需要至少 {{dashboard.feedback.minimumSample}} 条真实反馈。</div></section>
    </template>
  </section>
</template>
