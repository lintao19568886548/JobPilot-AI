<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'
import {
  addRecruiterInteraction, approveQueueItem, createApplication, createRecruiter,
  getApplication, getApplicationKpis, listApplications, listQueue, listRecruiters, prepareQueueItem,
  removeQueueItem, skipQueueItem, transitionApplication, updateQueueItem
} from '../../api/applications.js'

const loading = ref(true)
const actionLoading = ref(false)
const activeTab = ref('queue')
const queue = ref([])
const applications = ref([])
const applicationCounts = ref({})
const kpis = ref({ replied: 0 })
const recruiters = ref([])
const selectedApplication = ref(null)
const recruiterDialog = ref(false)
const interactionDialog = ref(false)
const transitionDialog = ref(false)
const interactionRecruiter = ref(null)

const recruiterForm = reactive({ name: '', position: '', platform: '', contactMasked: '', email: '', phone: '', communicationStatus: 'NEW', notes: '' })
const interactionForm = reactive({ applicationId: '', channel: 'PLATFORM', direction: 'NOTE', summary: '', followUpAt: null })
const transitionForm = reactive({ toStatus: '', note: '' })

const activeQueue = computed(() => queue.value.filter((item) => !['SUCCESS', 'FAILED', 'SKIPPED'].includes(item.status)).length)
const needsReview = computed(() => queue.value.filter((item) => ['NEED_REVIEW', 'APPROVED', 'PREPARED', 'BLOCKED'].includes(item.status)).length)
const replied = computed(() => Number(kpis.value.replied || 0))

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [queueResponse, applicationResponse, recruiterResponse, kpiResponse] = await Promise.all([listQueue(), listApplications(), listRecruiters(), getApplicationKpis()])
    queue.value = queueResponse.data.data
    applications.value = applicationResponse.data.data.items
    applicationCounts.value = applicationResponse.data.data.statusCounts || {}
    recruiters.value = recruiterResponse.data.data
    kpis.value = kpiResponse.data.data
    if (selectedApplication.value) {
      const match = applications.value.find((item) => item.id === selectedApplication.value.id)
      if (match) await selectApplication(match.id)
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '申请中心加载失败'))
  } finally {
    loading.value = false
  }
}

async function savePriority(item, value) {
  actionLoading.value = true
  try {
    await updateQueueItem(item.id, { priority: value, version: item.version })
    ElMessage.success('优先级已保存')
    await loadAll()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '优先级保存失败')) }
  finally { actionLoading.value = false }
}

async function approve(item) {
  await runAction(() => approveQueueItem(item.id, item.version), '队列项已批准；仍未进行外部投递')
}

async function prepare(item) {
  try {
    const response = await prepareQueueItem(item.id, item.version)
    const result = response.data.data
    if (result.externallySubmitted || result.applicationCreated) throw new Error('安全边界校验失败')
    ElMessage.success('辅助申请资料已准备；未进行外部提交，也未自动创建申请记录')
    await loadAll()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '辅助申请资料准备失败')) }
}

async function confirmApplied(item) {
  try {
    await ElMessageBox.confirm(
      '仅当你已经在外部平台亲自完成提交时继续。JobPilot 不会代替你打开页面或点击提交。',
      '确认外部投递事实', { confirmButtonText: '我已亲自完成投递', cancelButtonText: '尚未投递', type: 'warning' }
    )
    actionLoading.value = true
    await createApplication({
      queueItemId: item.id,
      mode: item.mode,
      confirmedExternalSubmission: true,
      notes: '由用户在申请中心明确确认'
    }, `application-ui-${crypto.randomUUID()}`)
    ElMessage.success('已记录真实投递事实并创建不可变时间线')
    activeTab.value = 'crm'
    await loadAll()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '记录投递失败'))
  } finally { actionLoading.value = false }
}

async function skip(item) { await runAction(() => skipQueueItem(item.id, item.version), '已跳过，历史仍保留') }
async function remove(item) {
  try {
    await ElMessageBox.confirm('移除队列项？已投递历史不可在这里删除。', '移除队列项')
    await runAction(() => removeQueueItem(item.id), '队列项已移除')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '移除失败')) }
}

async function runAction(call, success) {
  actionLoading.value = true
  try { await call(); ElMessage.success(success); await loadAll() }
  catch (error) { ElMessage.error(apiErrorMessage(error, '操作失败')) }
  finally { actionLoading.value = false }
}

async function selectApplication(id) {
  try {
    const response = await getApplication(id)
    selectedApplication.value = response.data.data
  } catch (error) { ElMessage.error(apiErrorMessage(error, '投递详情加载失败')) }
}

function openTransition() {
  transitionForm.toStatus = selectedApplication.value?.allowedTransitions?.[0] || ''
  transitionForm.note = ''
  transitionDialog.value = true
}

async function submitTransition() {
  if (!transitionForm.toStatus) return
  actionLoading.value = true
  try {
    const response = await transitionApplication(selectedApplication.value.id, {
      toStatus: transitionForm.toStatus,
      source: 'USER',
      note: transitionForm.note || null,
      evidence: { enteredBy: 'user', channel: 'application-center' },
      version: selectedApplication.value.version
    })
    selectedApplication.value = response.data.data
    transitionDialog.value = false
    ElMessage.success('状态已更新，历史日志已追加')
    await loadAll()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '状态迁移失败')) }
  finally { actionLoading.value = false }
}

async function submitRecruiter() {
  actionLoading.value = true
  try {
    await createRecruiter({ ...recruiterForm })
    recruiterDialog.value = false
    Object.assign(recruiterForm, { name: '', position: '', platform: '', contactMasked: '', email: '', phone: '', communicationStatus: 'NEW', notes: '' })
    ElMessage.success('招聘人员档案已创建')
    await loadAll()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '招聘人员创建失败')) }
  finally { actionLoading.value = false }
}

function openInteraction(recruiter) {
  interactionRecruiter.value = recruiter
  Object.assign(interactionForm, { applicationId: selectedApplication.value?.id || '', channel: 'PLATFORM', direction: 'NOTE', summary: '', followUpAt: null })
  interactionDialog.value = true
}

async function submitInteraction() {
  actionLoading.value = true
  try {
    await addRecruiterInteraction(interactionRecruiter.value.id, { ...interactionForm, applicationId: interactionForm.applicationId || null })
    interactionDialog.value = false
    ElMessage.success('人工沟通记录已追加；没有发送任何消息')
    await loadAll()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '沟通记录保存失败')) }
  finally { actionLoading.value = false }
}

function statusLabel(status) {
  const labels = { WAITING: '等待', READY: '可处理', NEED_REVIEW: '需复核', APPROVED: '已批准', PREPARED: '已准备', SUCCESS: '已建档', SKIPPED: '已跳过', BLOCKED: '受阻', APPLIED: '已投递', VIEWED: '已查看', REPLIED: '已回复', WRITTEN_TEST: '笔试', INTERVIEW_1: '一面', INTERVIEW_2: '二面', INTERVIEW_3: '三面', HR_INTERVIEW: 'HR 面', OFFER: 'Offer', REJECTED: '拒绝', WITHDRAWN: '撤回', CLOSED: '关闭' }
  return labels[status] || status
}
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
</script>

<template>
  <section v-loading="loading" class="page-section application-center">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 5 · 人工确认的求职管理</p><h1>申请中心</h1><p>队列准备、人工确认、求职关系时间线与招聘人员记录；系统不会自动提交或发送消息。</p></div>
      <router-link to="/recommendations" class="text-action">从推荐中选择岗位 →</router-link>
    </div>

    <div class="application-metrics">
      <div><span>活动队列</span><strong>{{ activeQueue }}</strong></div>
      <div><span>需要处理</span><strong>{{ needsReview }}</strong></div>
      <div><span>投递记录</span><strong>{{ applications.length }}</strong></div>
      <div><span>收到回复</span><strong>{{ replied }}</strong></div>
      <div><span>招聘人员</span><strong>{{ recruiters.length }}</strong></div>
    </div>

    <div class="application-tabs">
      <button :class="{ active: activeTab === 'queue' }" @click="activeTab = 'queue'">申请队列 <span>{{ queue.length }}</span></button>
      <button :class="{ active: activeTab === 'crm' }" @click="activeTab = 'crm'">投递进度 <span>{{ applications.length }}</span></button>
      <button :class="{ active: activeTab === 'recruiters' }" @click="activeTab = 'recruiters'">招聘人员 <span>{{ recruiters.length }}</span></button>
    </div>

    <div v-if="activeTab === 'queue'" class="surface application-table">
      <header><div><span class="section-kicker">安全准备</span><h2>投递队列</h2></div><p>批准和准备操作都不会产生外部提交。</p></header>
      <div class="application-table-head"><span>岗位</span><span>模式 / 简历</span><span>优先级</span><span>状态</span><span>操作</span></div>
      <article v-for="item in queue" :key="item.id" class="application-table-row">
        <div><strong>{{ item.job.title }}</strong><span>{{ item.job.companyName || '未知公司' }} · {{ item.job.city || '地点未披露' }}</span><small>{{ displayText(item.job.platform) }} · {{ time(item.createdAt) }}</small></div>
        <div><span class="mode-label">{{ displayText(item.mode) }}</span><small>{{ item.resume.resumeName }} · v{{ item.resume.versionNumber }}</small></div>
        <el-input-number :model-value="item.priority" :min="0" :max="100" size="small" @change="(value) => savePriority(item, value)" />
        <span class="status-label" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</span>
        <div class="queue-actions">
          <el-button v-if="['READY','NEED_REVIEW'].includes(item.status)" size="small" @click="approve(item)">批准</el-button>
          <el-button v-if="item.mode === 'ASSIST' && item.status === 'APPROVED'" size="small" @click="prepare(item)">准备材料</el-button>
          <el-button v-if="['APPROVED','PREPARED'].includes(item.status)" type="primary" size="small" @click="confirmApplied(item)">确认已投</el-button>
          <el-button v-if="!['SUCCESS','SKIPPED','FAILED'].includes(item.status)" text size="small" @click="skip(item)">跳过</el-button>
          <el-button v-if="item.status !== 'SUCCESS'" text size="small" @click="remove(item)">移除</el-button>
        </div>
      </article>
      <div v-if="!queue.length" class="empty-state">队列为空。请从 AI 岗位推荐选择真实岗位加入。</div>
    </div>

    <div v-else-if="activeTab === 'crm'" class="application-crm-grid">
      <aside class="surface crm-list">
        <header><span class="section-kicker">投递进度</span><h2>投递记录</h2></header>
        <button v-for="item in applications" :key="item.id" :class="{ active: selectedApplication?.id === item.id }" @click="selectApplication(item.id)">
          <div><strong>{{ item.job.title }}</strong><span>{{ item.job.companyName || '未知公司' }}</span></div><em>{{ statusLabel(item.status) }}</em><small>{{ time(item.lastStatusAt) }}</small>
        </button>
        <div v-if="!applications.length" class="empty-state compact">尚无已确认的投递事实。</div>
      </aside>
      <main class="surface crm-detail">
        <template v-if="selectedApplication">
          <header><div><span class="section-kicker">申请事实</span><h2>{{ selectedApplication.job.title }}</h2><p>{{ selectedApplication.job.companyName }} · {{ displayText(selectedApplication.applicationMode) }}</p></div><el-button v-if="selectedApplication.allowedTransitions.length" type="primary" @click="openTransition">更新状态</el-button></header>
          <div class="crm-facts"><div><span>当前状态</span><strong>{{ statusLabel(selectedApplication.status) }}</strong></div><div><span>投递时间</span><strong>{{ time(selectedApplication.appliedAt) }}</strong></div><div><span>简历版本</span><strong>v{{ selectedApplication.resume.versionNumber }}</strong></div></div>
          <section class="application-timeline"><h3>不可变时间线</h3><article v-for="log in selectedApplication.timeline" :key="log.id"><i /><div><strong>{{ statusLabel(log.toStatus) }}</strong><span>{{ log.note || displayText(log.eventType) }}</span><small>{{ displayText(log.source) }} · {{ time(log.occurredAt) }} · 链路标识 {{ log.traceId }}</small></div></article></section>
        </template>
        <div v-else class="empty-state centered">选择投递记录查看完整时间线。</div>
      </main>
    </div>

    <div v-else class="surface recruiter-workspace">
      <header><div><span class="section-kicker">人工关系记录</span><h2>招聘人员</h2></div><el-button type="primary" @click="recruiterDialog = true">新增招聘人员</el-button></header>
      <article v-for="recruiter in recruiters" :key="recruiter.id" class="recruiter-row">
        <div><strong>{{ recruiter.name }}</strong><span>{{ recruiter.position || '职位未记录' }} · {{ recruiter.companyName || recruiter.platform || '来源未记录' }}</span></div>
        <div><span>{{ displayText(recruiter.communicationStatus) }}</span><small>上次联系 {{ time(recruiter.lastContactAt) }}</small></div>
        <div><span>下次跟进</span><small>{{ time(recruiter.nextFollowUpAt) }}</small></div>
        <el-button size="small" @click="openInteraction(recruiter)">记录沟通</el-button>
      </article>
      <div v-if="!recruiters.length" class="empty-state">尚无招聘人员档案。这里只记录人工沟通，不自动发送消息。</div>
    </div>

    <el-dialog v-model="transitionDialog" title="更新申请状态" width="440px">
      <el-form label-position="top"><el-form-item label="下一状态"><el-select v-model="transitionForm.toStatus" class="full-button"><el-option v-for="status in selectedApplication?.allowedTransitions || []" :key="status" :value="status" :label="statusLabel(status)" /></el-select></el-form-item><el-form-item label="事实备注"><el-input v-model="transitionForm.note" type="textarea" :rows="3" maxlength="2000" /></el-form-item></el-form><template #footer><el-button @click="transitionDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitTransition">追加状态事件</el-button></template>
    </el-dialog>

    <el-dialog v-model="recruiterDialog" title="新增招聘人员" width="520px">
      <el-form label-position="top" class="form-grid two"><el-form-item label="姓名"><el-input v-model="recruiterForm.name" maxlength="120" /></el-form-item><el-form-item label="职位"><el-input v-model="recruiterForm.position" /></el-form-item><el-form-item label="平台"><el-input v-model="recruiterForm.platform" /></el-form-item><el-form-item label="脱敏联系方式"><el-input v-model="recruiterForm.contactMasked" /></el-form-item><el-form-item label="邮箱"><el-input v-model="recruiterForm.email" /></el-form-item><el-form-item label="电话"><el-input v-model="recruiterForm.phone" /></el-form-item><el-form-item label="备注" class="wide"><el-input v-model="recruiterForm.notes" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="recruiterDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitRecruiter">保存档案</el-button></template>
    </el-dialog>

    <el-dialog v-model="interactionDialog" title="记录人工沟通" width="500px">
      <el-form label-position="top"><el-form-item label="渠道"><el-select v-model="interactionForm.channel"><el-option v-for="value in ['PLATFORM','EMAIL','PHONE','WECHAT','SMS','OTHER']" :key="value" :value="value" :label="displayText(value)" /></el-select></el-form-item><el-form-item label="方向"><el-radio-group v-model="interactionForm.direction"><el-radio-button value="NOTE">记录</el-radio-button><el-radio-button value="OUTBOUND">我发出</el-radio-button><el-radio-button value="INBOUND">对方回复</el-radio-button></el-radio-group></el-form-item><el-form-item label="摘要"><el-input v-model="interactionForm.summary" type="textarea" :rows="4" maxlength="2000" /></el-form-item></el-form><p class="manual-boundary">保存只会写入本地求职关系记录，不会发送任何消息。</p><template #footer><el-button @click="interactionDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitInteraction">保存记录</el-button></template>
    </el-dialog>
  </section>
</template>
