<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'
import {
  activateKnowledgeGap, addAnswerNote, cancelInterviewReminder, completeInterviewReminder,
  confirmInterviewReview, createInterview, createInterviewQuestion, createInterviewReminder,
  createInterviewRound, deleteInterview, dismissKnowledgeGap, generateInterviewReview, getInterview,
  listInterviewReminders, listInterviewReviews, listInterviews, listKnowledgeGaps, predictQuestions,
  resolveKnowledgeGap, updateInterview, updateInterviewRound
} from '../../api/interviews.js'

const loading = ref(true)
const actionLoading = ref(false)
const viewMode = ref('list')
const activePanel = ref('interview')
const interviews = ref([])
const selected = ref(null)
const reviews = ref([])
const gaps = ref([])
const reminders = ref([])
const createDialog = ref(false)
const questionDialog = ref(false)
const answerDialog = ref(false)
const reminderDialog = ref(false)
const activeRound = ref(null)
const activeQuestion = ref(null)
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'

const initialStart = futureLocal(1, 10)
const interviewForm = reactive({
  companyName: '', role: '', timezone, notes: '', roundType: 'TECHNICAL', roundTitle: '技术面试',
  roundStart: initialStart, roundEnd: futureLocal(1, 11), format: 'ONLINE', meetingLink: '', location: '', interviewerName: ''
})
const questionForm = reactive({ sourceType: 'ACTUAL', category: 'TECHNICAL_FOUNDATION', difficulty: 'MEDIUM', question: '', purpose: '', answerFramework: '' })
const answerForm = reactive({ answer: '', selfRating: 3 })
const reminderForm = reactive({ reminderType: 'PREPARE', title: '准备面试材料', remindAt: futureLocal(0, new Date().getHours() + 2) })

const calendarRounds = computed(() => interviews.value.flatMap((item) => (item.rounds || []).map((round) => ({ ...round, interview: item }))).sort((a, b) => new Date(a.scheduledStartAt) - new Date(b.scheduledStartAt)))
const proposedGaps = computed(() => gaps.value.filter((item) => item.status === 'PROPOSED'))
const activeGaps = computed(() => gaps.value.filter((item) => item.status === 'ACTIVE'))
const pendingReminders = computed(() => reminders.value.filter((item) => item.status === 'PENDING'))
const selectedGaps = computed(() => gaps.value.filter((item) => item.sourceInterviewId === selected.value?.id))
const selectedReminders = computed(() => reminders.value.filter((item) => item.interviewId === selected.value?.id))

onMounted(loadAll)

async function loadAll(preferredId) {
  loading.value = true
  try {
    const [interviewResponse, gapResponse, reminderResponse] = await Promise.all([
      listInterviews({ page: 1, size: 100 }), listKnowledgeGaps(), listInterviewReminders()
    ])
    interviews.value = interviewResponse.data.data.items
    gaps.value = gapResponse.data.data
    reminders.value = reminderResponse.data.data
    const id = preferredId || selected.value?.id || interviews.value[0]?.id
    if (id) await selectInterview(id)
    else { selected.value = null; reviews.value = [] }
  } catch (error) { ElMessage.error(apiErrorMessage(error, '面试中心加载失败')) }
  finally { loading.value = false }
}

async function selectInterview(id) {
  try {
    const [detail, reviewResponse] = await Promise.all([getInterview(id), listInterviewReviews(id)])
    selected.value = detail.data.data
    reviews.value = reviewResponse.data.data
    activePanel.value = 'interview'
  } catch (error) { ElMessage.error(apiErrorMessage(error, '面试详情加载失败')) }
}

async function submitInterview() {
  if (!interviewForm.companyName.trim() || !interviewForm.role.trim()) return ElMessage.warning('请填写公司与岗位')
  actionLoading.value = true
  try {
    const created = await createInterview({
      companyName: interviewForm.companyName.trim(), role: interviewForm.role.trim(), timezone: interviewForm.timezone,
      notes: interviewForm.notes || null
    })
    await createInterviewRound(created.data.data.id, {
      roundNo: 1, roundType: interviewForm.roundType, title: interviewForm.roundTitle,
      scheduledStartAt: localWithOffset(interviewForm.roundStart), scheduledEndAt: localWithOffset(interviewForm.roundEnd),
      timezone: interviewForm.timezone, format: interviewForm.format, meetingLink: interviewForm.meetingLink || null,
      location: interviewForm.location || null, interviewerName: interviewForm.interviewerName || null, notes: null
    })
    createDialog.value = false
    ElMessage.success('面试与首轮安排已保存到真实数据库')
    await loadAll(created.data.data.id)
  } catch (error) { ElMessage.error(apiErrorMessage(error, '创建面试失败')) }
  finally { actionLoading.value = false }
}

async function setInterviewStatus(status) {
  if (!selected.value) return
  await run(async () => {
    await updateInterview(selected.value.id, {
      companyName: selected.value.companyName, role: selected.value.role, status,
      result: selected.value.result, timezone: selected.value.timezone, notes: selected.value.notes,
      version: selected.value.version
    })
  }, `面试状态已更新为 ${status}`, selected.value.id)
}

async function setRoundStatus(round, status) {
  await run(() => updateInterviewRound(round.id, {
    roundNo: round.roundNo, roundType: round.roundType, title: round.title,
    scheduledStartAt: localWithOffset(localInput(round.scheduledStartAt)),
    scheduledEndAt: localWithOffset(localInput(round.scheduledEndAt)), timezone: round.timezone,
    format: round.format, meetingLink: round.meetingLink, location: round.location,
    interviewerName: round.interviewerName, status, result: round.result, notes: round.notes, version: round.version
  }), `轮次状态已更新为 ${status}`, selected.value.id)
}

async function removeInterview() {
  try {
    await ElMessageBox.confirm('删除后普通查询不再显示；复盘与版本历史不会被物理删除。', '删除面试')
    await run(() => deleteInterview(selected.value.id), '面试已逻辑删除')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '删除失败')) }
}

async function predict(round) {
  await run(() => predictQuestions(round.id, `prediction-ui-${crypto.randomUUID()}`), '已生成预测题；所有题目均标记为“预测题”', selected.value.id)
}

function openQuestion(round) {
  activeRound.value = round
  Object.assign(questionForm, { sourceType: 'ACTUAL', category: 'TECHNICAL_FOUNDATION', difficulty: 'MEDIUM', question: '', purpose: '', answerFramework: '' })
  questionDialog.value = true
}

async function submitQuestion() {
  if (!questionForm.question.trim()) return
  await run(() => createInterviewQuestion(activeRound.value.id, {
    ...questionForm, purpose: questionForm.purpose || null, basis: '由用户手工记录',
    answerFramework: questionForm.answerFramework || null, suggestedFollowUps: [], riskNotes: null, displayOrder: 100
  }), questionForm.sourceType === 'ACTUAL' ? '实际面试题已记录' : '手工练习题已记录', selected.value.id)
  questionDialog.value = false
}

function openAnswer(question) {
  activeQuestion.value = question
  answerForm.answer = question.answerNotes?.[0]?.answer || ''
  answerForm.selfRating = question.answerNotes?.[0]?.selfRating || 3
  answerDialog.value = true
}

async function submitAnswer() {
  if (!answerForm.answer.trim()) return
  await run(() => addAnswerNote(activeQuestion.value.id, {
    answer: answerForm.answer.trim(), selfRating: answerForm.selfRating, recordedAt: new Date().toISOString()
  }), 'Answer Note 已追加为新版本', selected.value.id)
  answerDialog.value = false
}

async function generateReview() {
  await run(() => generateInterviewReview(selected.value.id, `review-ui-${crypto.randomUUID()}`), '复盘草稿已生成，仅使用用户记录的实际问答', selected.value.id)
}

async function confirmReview(review) {
  try {
    await ElMessageBox.confirm('确认后该复盘版本被冻结；知识缺口仍需逐条人工激活。', '确认复盘版本')
    await run(() => confirmInterviewReview(review.id, review.version), '复盘版本已确认', selected.value.id)
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '确认失败')) }
}

async function gapAction(gap, action) {
  const calls = { activate: activateKnowledgeGap, resolve: resolveKnowledgeGap, dismiss: dismissKnowledgeGap }
  const labels = { activate: '知识缺口已由你确认并激活', resolve: '知识缺口已解决', dismiss: '建议已驳回' }
  if (action === 'activate') {
    try { await ElMessageBox.confirm('激活代表你确认该建议确实是需要补足的缺口。不会自动修改技能或简历。', '人工确认知识缺口') }
    catch (error) { if (error === 'cancel' || error === 'close') return }
  }
  await run(() => calls[action](gap.id, gap.version), labels[action], selected.value?.id)
}

function openReminder(round) {
  activeRound.value = round || selected.value?.rounds?.[0] || null
  reminderForm.reminderType = 'PREPARE'
  reminderForm.title = `准备 ${selected.value?.companyName || ''} ${selected.value?.role || ''}`.trim()
  const target = activeRound.value ? new Date(activeRound.value.scheduledStartAt) : new Date(Date.now() + 7200000)
  target.setHours(target.getHours() - 2)
  if (target <= new Date()) target.setTime(Date.now() + 7200000)
  reminderForm.remindAt = localInput(target)
  reminderDialog.value = true
}

async function submitReminder() {
  await run(() => createInterviewReminder({
    interviewId: selected.value.id, roundId: activeRound.value?.id || null,
    reminderType: reminderForm.reminderType, title: reminderForm.title,
    remindAt: localWithOffset(reminderForm.remindAt), timezone: selected.value.timezone
  }), '站内提醒已创建；不会发送外部消息', selected.value.id)
  reminderDialog.value = false
}

async function reminderAction(reminder, action) {
  const call = action === 'done' ? completeInterviewReminder : cancelInterviewReminder
  await run(() => call(reminder.id, reminder.version), action === 'done' ? '提醒已完成' : '提醒已取消', selected.value?.id)
}

async function run(call, success, preferredId) {
  actionLoading.value = true
  try { await call(); ElMessage.success(success); await loadAll(preferredId) }
  catch (error) { ElMessage.error(apiErrorMessage(error, '操作失败')) }
  finally { actionLoading.value = false }
}

function futureLocal(dayOffset, hour) {
  const value = new Date()
  value.setDate(value.getDate() + dayOffset)
  value.setHours(hour, 0, 0, 0)
  return localInput(value)
}
function localInput(value) {
  const date = value instanceof Date ? value : new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function localWithOffset(value) {
  const date = new Date(value)
  const total = -date.getTimezoneOffset()
  const sign = total >= 0 ? '+' : '-'
  const pad = (number) => String(number).padStart(2, '0')
  return `${value.length === 16 ? value + ':00' : value}${sign}${pad(Math.floor(Math.abs(total) / 60))}:${pad(Math.abs(total) % 60)}`
}
function time(value) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
function label(value) {
  const labels = { SCHEDULED: '已安排', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '未出席',
    PLANNED: '待进行', PENDING: '待确认', DONE: '已完成', DRAFT: '草稿', CONFIRMED: '已确认', PROPOSED: '待确认', ACTIVE: '补习中', RESOLVED: '已解决', DISMISSED: '已驳回',
    ACTUAL: '实际题', MANUAL: '手工题', PREDICTED: '预测题', ONLINE: '线上', ONSITE: '现场', PHONE: '电话', OTHER: '其他' }
  return labels[value] || displayText(value)
}
</script>

<template>
  <section v-loading="loading" class="page-section interview-center">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 8 · 基于证据的面试准备</p><h1>面试中心</h1><p>管理真实面试、预测题、用户记录的实际问答、版本化复盘和站内提醒。</p></div>
      <div class="heading-actions"><el-button @click="viewMode = viewMode === 'list' ? 'calendar' : 'list'">{{ viewMode === 'list' ? '日程视图' : '列表视图' }}</el-button><el-button type="primary" @click="createDialog = true">新增面试</el-button></div>
    </div>

    <div class="interview-boundary"><strong>人工全程确认</strong><span>不录音、不读取会议、不自动发消息、不代替用户面试；AI 输出均为预测或待确认建议。</span></div>

    <div class="interview-metrics">
      <div><span>面试</span><strong>{{ interviews.length }}</strong></div>
      <div><span>待办提醒</span><strong>{{ pendingReminders.length }}</strong></div>
      <div><span>待确认缺口</span><strong>{{ proposedGaps.length }}</strong></div>
      <div><span>补习中</span><strong>{{ activeGaps.length }}</strong></div>
    </div>

    <div v-if="viewMode === 'calendar'" class="surface interview-calendar">
      <header><div><span class="section-kicker">本地时间 · {{ timezone }}</span><h2>面试日程</h2></div></header>
      <button v-for="round in calendarRounds" :key="round.id" @click="viewMode = 'list'; selectInterview(round.interview.id)"><time>{{ time(round.scheduledStartAt) }}</time><div><strong>{{ round.interview.companyName }} · {{ round.interview.role }}</strong><span>{{ round.title }} · {{ label(round.format) }}</span></div><em>{{ label(round.status) }}</em></button>
      <div v-if="!calendarRounds.length" class="empty-state">尚无面试日程。</div>
    </div>

    <div v-else class="interview-workspace">
      <aside class="surface interview-list">
        <header><span class="section-kicker">面试</span><h2>面试列表</h2></header>
        <button v-for="item in interviews" :key="item.id" :class="{ active: selected?.id === item.id }" @click="selectInterview(item.id)"><div><strong>{{ item.companyName }}</strong><span>{{ item.role }}</span><small>{{ time(item.rounds?.[0]?.scheduledStartAt) }}</small></div><em>{{ label(item.status) }}</em></button>
        <div v-if="!interviews.length" class="empty-state compact">创建第一场真实面试记录。</div>
      </aside>

      <main class="surface interview-detail">
        <template v-if="selected">
          <header><div><span class="section-kicker">面试事实</span><h2>{{ selected.role }}</h2><p>{{ selected.companyName }} · {{ selected.timezone }}</p></div><div><el-button size="small" @click="openReminder(selected.rounds?.[0])">新建提醒</el-button><el-dropdown><el-button size="small">状态 ▾</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="setInterviewStatus('IN_PROGRESS')">进行中</el-dropdown-item><el-dropdown-item @click="setInterviewStatus('COMPLETED')">已完成</el-dropdown-item><el-dropdown-item @click="setInterviewStatus('CANCELLED')">已取消</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button text size="small" @click="removeInterview">删除</el-button></div></header>

          <div class="interview-tabs"><button :class="{active: activePanel === 'interview'}" @click="activePanel = 'interview'">轮次与题目</button><button :class="{active: activePanel === 'reviews'}" @click="activePanel = 'reviews'">复盘版本 {{ reviews.length }}</button><button :class="{active: activePanel === 'gaps'}" @click="activePanel = 'gaps'">知识缺口 {{ selectedGaps.length }}</button><button :class="{active: activePanel === 'reminders'}" @click="activePanel = 'reminders'">提醒 {{ selectedReminders.length }}</button></div>

          <section v-if="activePanel === 'interview'" class="rounds">
            <article v-for="round in selected.rounds" :key="round.id" class="round-block">
              <header><div><small>第 {{ round.roundNo }} 轮 · {{ label(round.roundType) }}</small><h3>{{ round.title }}</h3><p>{{ time(round.scheduledStartAt) }}—{{ time(round.scheduledEndAt) }} · {{ label(round.format) }}<template v-if="round.interviewerName"> · {{ round.interviewerName }}</template></p></div><span class="plain-tag">{{ label(round.status) }}</span></header>
              <div class="round-actions"><el-button size="small" @click="predict(round)">生成预测题</el-button><el-button size="small" @click="openQuestion(round)">记录实际题</el-button><el-button v-if="round.status === 'PLANNED'" text size="small" @click="setRoundStatus(round, 'COMPLETED')">标记完成</el-button></div>
              <div class="question-list">
                <article v-for="question in round.questions" :key="question.id"><div class="question-meta"><span :class="`source-${question.sourceType.toLowerCase()}`">{{ label(question.sourceType) }}</span><small>{{ label(question.category) }} · {{ label(question.difficulty) }}</small></div><strong>{{ question.question }}</strong><p v-if="question.answerFramework">{{ question.answerFramework }}</p><div v-if="question.answerNotes?.length" class="latest-answer"><small>回答记录 v{{ question.answerNotes[0].noteVersion }} · 自评 {{ question.answerNotes[0].selfRating || '—' }}/5</small><p>{{ question.answerNotes[0].answer }}</p></div><el-button v-if="question.sourceType === 'ACTUAL'" text size="small" @click="openAnswer(question)">{{ question.answerNotes?.length ? '追加回答记录' : '记录回答' }}</el-button></article>
              </div>
            </article>
            <div v-if="!selected.rounds?.length" class="empty-state">尚未添加轮次。</div>
          </section>

          <section v-else-if="activePanel === 'reviews'" class="review-panel">
            <div class="panel-action"><div><h3>不可变复盘版本</h3><p>只分析用户主动记录的实际问答，不使用录音或会议数据。</p></div><el-button type="primary" @click="generateReview">生成新复盘版本</el-button></div>
            <article v-for="review in reviews" :key="review.id" class="review-version"><header><div><strong>复盘 v{{ review.reviewVersion }}</strong><span>{{ label(review.status) }} · {{ label(review.sourceType) }} · {{ time(review.generatedAt) }}</span></div><el-button v-if="review.status === 'DRAFT'" size="small" @click="confirmReview(review)">人工确认版本</el-button></header><p>{{ review.summary }}</p><div v-for="item in review.items" :key="item.id" class="review-item"><span>{{ label(item.itemType) }} · {{ label(item.severity) }}</span><strong>{{ item.title }}</strong><p>{{ item.description }}</p><small>证据：{{ item.evidence }}</small></div></article>
            <div v-if="!reviews.length" class="empty-state">记录至少一道实际题及回答记录后生成复盘。</div>
          </section>

          <section v-else-if="activePanel === 'gaps'" class="gap-panel">
            <div class="panel-action"><div><h3>知识缺口</h3><p>AI 只提出待确认建议；必须由你逐条激活。</p></div></div>
            <article v-for="gap in selectedGaps" :key="gap.id"><div><span>{{ label(gap.category) }} · {{ label(gap.severity) }}</span><strong>{{ gap.title }}</strong><p>{{ gap.description }}</p><small>{{ label(gap.status) }}</small></div><div><el-button v-if="gap.status === 'PROPOSED'" size="small" type="primary" @click="gapAction(gap, 'activate')">确认并激活</el-button><el-button v-if="gap.status === 'PROPOSED'" size="small" @click="gapAction(gap, 'dismiss')">驳回</el-button><el-button v-if="gap.status === 'ACTIVE'" size="small" @click="gapAction(gap, 'resolve')">标记已解决</el-button></div></article>
            <div v-if="!selectedGaps.length" class="empty-state">尚无知识缺口建议。</div>
          </section>

          <section v-else class="reminder-panel">
            <div class="panel-action"><div><h3>站内提醒</h3><p>提醒只保存在 JobPilot，不发送邮件、短信或平台消息。</p></div><el-button @click="openReminder(selected.rounds?.[0])">新建提醒</el-button></div>
            <article v-for="reminder in selectedReminders" :key="reminder.id"><div><span>{{ label(reminder.reminderType) }} · {{ label(reminder.status) }}</span><strong>{{ reminder.title }}</strong><p>{{ time(reminder.remindAt) }} · {{ reminder.timezone }}</p></div><div><el-button v-if="reminder.status === 'PENDING'" size="small" @click="reminderAction(reminder, 'done')">完成</el-button><el-button v-if="reminder.status === 'PENDING'" text size="small" @click="reminderAction(reminder, 'cancel')">取消</el-button></div></article>
          </section>
        </template>
        <div v-else class="empty-state centered">选择或创建一场面试。</div>
      </main>
    </div>

    <el-dialog v-model="createDialog" title="新增面试" width="680px"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="公司"><el-input v-model="interviewForm.companyName" maxlength="200" /></el-form-item><el-form-item label="岗位"><el-input v-model="interviewForm.role" maxlength="200" /></el-form-item><el-form-item label="首轮类型"><el-select v-model="interviewForm.roundType"><el-option v-for="item in ['PHONE_SCREEN','TECHNICAL','SYSTEM_DESIGN','BEHAVIORAL','HR','MANAGER','FINAL','OTHER']" :key="item" :value="item" :label="label(item)" /></el-select></el-form-item><el-form-item label="轮次标题"><el-input v-model="interviewForm.roundTitle" /></el-form-item><el-form-item label="开始时间"><el-date-picker v-model="interviewForm.roundStart" type="datetime" value-format="YYYY-MM-DDTHH:mm" /></el-form-item><el-form-item label="结束时间"><el-date-picker v-model="interviewForm.roundEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm" /></el-form-item><el-form-item label="形式"><el-select v-model="interviewForm.format"><el-option v-for="item in ['ONLINE','ONSITE','PHONE','OTHER']" :key="item" :value="item" :label="label(item)" /></el-select></el-form-item><el-form-item label="时区"><el-input v-model="interviewForm.timezone" readonly /></el-form-item><el-form-item label="会议链接（可选）"><el-input v-model="interviewForm.meetingLink" maxlength="1000" /></el-form-item><el-form-item label="面试官（可选）"><el-input v-model="interviewForm.interviewerName" maxlength="160" /></el-form-item></div><el-form-item label="备注"><el-input v-model="interviewForm.notes" type="textarea" :rows="2" maxlength="4000" /></el-form-item></el-form><template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitInterview">保存面试与首轮</el-button></template></el-dialog>

    <el-dialog v-model="questionDialog" title="记录面试题" width="560px"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="来源"><el-select v-model="questionForm.sourceType"><el-option value="ACTUAL" label="实际面试题" /><el-option value="MANUAL" label="手工练习题" /></el-select></el-form-item><el-form-item label="难度"><el-select v-model="questionForm.difficulty"><el-option v-for="item in ['EASY','MEDIUM','HARD']" :key="item" :value="item" :label="label(item)" /></el-select></el-form-item></div><el-form-item label="分类"><el-select v-model="questionForm.category"><el-option v-for="item in ['TECHNICAL_FOUNDATION','PROJECT_DEEP_DIVE','SYSTEM_DESIGN','BEHAVIORAL','ROLE_RISK','FOLLOW_UP','OTHER']" :key="item" :value="item" :label="label(item)" /></el-select></el-form-item><el-form-item label="问题原文"><el-input v-model="questionForm.question" type="textarea" :rows="4" maxlength="4000" /></el-form-item><el-form-item label="回答框架（可选）"><el-input v-model="questionForm.answerFramework" type="textarea" :rows="3" maxlength="20000" /></el-form-item></el-form><template #footer><el-button @click="questionDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitQuestion">保存题目</el-button></template></el-dialog>

    <el-dialog v-model="answerDialog" title="追加回答记录" width="560px"><p class="manual-boundary">每次保存都会创建新版本，不覆盖旧回答。</p><el-form label-position="top"><el-form-item label="回答记录"><el-input v-model="answerForm.answer" type="textarea" :rows="7" maxlength="30000" /></el-form-item><el-form-item label="自评（1–5）"><el-rate v-model="answerForm.selfRating" /></el-form-item></el-form><template #footer><el-button @click="answerDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitAnswer">追加新版本</el-button></template></el-dialog>

    <el-dialog v-model="reminderDialog" title="新建站内提醒" width="520px"><el-form label-position="top"><el-form-item label="类型"><el-select v-model="reminderForm.reminderType"><el-option v-for="item in ['PREPARE','START','REVIEW','CUSTOM']" :key="item" :value="item" :label="label(item)" /></el-select></el-form-item><el-form-item label="标题"><el-input v-model="reminderForm.title" maxlength="240" /></el-form-item><el-form-item label="提醒时间"><el-date-picker v-model="reminderForm.remindAt" type="datetime" value-format="YYYY-MM-DDTHH:mm" /></el-form-item><p class="muted">时区：{{ selected?.timezone || timezone }}</p></el-form><template #footer><el-button @click="reminderDialog = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitReminder">创建站内提醒</el-button></template></el-dialog>
  </section>
</template>
