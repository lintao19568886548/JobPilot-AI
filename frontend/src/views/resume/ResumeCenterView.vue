<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import {
  createResume, createResumeVersion, deleteResume, getResume, getResumeVersion,
  listResumes, setDefaultResume, setMasterResume, updateResume
} from '../../api/resume.js'
import { apiErrorMessage } from '../../api/http.js'
import { getResumeVersionMetrics } from '../../api/tailoring.js'
import { displayText } from '../../utils/displayText.js'

const loading = ref(true)
const resumes = ref([])
const selected = ref(null)
const selectedDetail = ref(null)
const resumeDialog = ref(false)
const versionDialog = ref(false)
const versionDrawer = ref(false)
const editingResume = ref(false)
const activeVersion = ref(null)
const versionMetrics = ref(null)

const resumeForm = reactive(emptyResume())
const versionForm = reactive(emptyVersion())
const sectionTypes = ['BASIC_INFO','SUMMARY','EDUCATION','SKILLS','EXPERIENCE','PROJECTS','CERTIFICATIONS','AWARDS','OPEN_SOURCE','OTHER']

onMounted(loadResumes)

async function loadResumes(preferredId) {
  loading.value = true
  try {
    const { data } = await listResumes()
    resumes.value = data.data
    const id = preferredId || selected.value?.id || resumes.value[0]?.id
    if (id) await selectResume(id)
    else { selected.value = null; selectedDetail.value = null }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '简历列表加载失败'))
  } finally { loading.value = false }
}

async function selectResume(id) {
  const { data } = await getResume(id)
  selectedDetail.value = data.data
  selected.value = data.data.resume
}

function openCreate() {
  editingResume.value = false
  Object.assign(resumeForm, emptyResume())
  resumeDialog.value = true
}

function openEdit() {
  if (!selected.value) return
  editingResume.value = true
  Object.assign(resumeForm, {
    name: selected.value.name,
    targetRole: selected.value.targetRole,
    description: selected.value.description,
    status: selected.value.status,
    master: selected.value.master,
    defaultResume: selected.value.defaultResume
  })
  resumeDialog.value = true
}

async function saveResume() {
  if (!resumeForm.name.trim()) {
    ElMessage.warning('请输入简历名称')
    return
  }
  try {
    if (editingResume.value) {
      await updateResume(selected.value.id, {
        name: resumeForm.name,
        targetRole: resumeForm.targetRole,
        description: resumeForm.description,
        status: resumeForm.status
      })
      resumeDialog.value = false
      await loadResumes(selected.value.id)
      ElMessage.success('简历信息已更新')
    } else {
      const { data } = await createResume(resumeForm)
      resumeDialog.value = false
      await loadResumes(data.data.resume.id)
      ElMessage.success('简历已创建')
    }
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

function openVersion() {
  if (!selected.value) return
  Object.assign(versionForm, emptyVersion(), {
    versionName: `${selected.value.name} v${selected.value.versionCount + 1}`,
    contentText: JSON.stringify({ title: selected.value.name, targetRole: selected.value.targetRole || '' }, null, 2)
  })
  versionDialog.value = true
}

function addSection() {
  const used = new Set(versionForm.sections.map((item) => item.sectionType))
  const type = sectionTypes.find((item) => !used.has(item)) || 'OTHER'
  versionForm.sections.push({ sectionType: type, contentText: '{}', sortOrder: versionForm.sections.length })
}

function removeSection(index) {
  versionForm.sections.splice(index, 1)
}

async function saveVersion() {
  try {
    const content = JSON.parse(versionForm.contentText || '{}')
    const sections = versionForm.sections.map((item, index) => ({
      sectionType: item.sectionType,
      content: JSON.parse(item.contentText || '{}'),
      sortOrder: index
    }))
    const payload = {
      versionName: versionForm.versionName,
      content,
      renderedText: versionForm.renderedText,
      sourceType: 'MANUAL',
      createdBy: 'USER',
      sections
    }
    await createResumeVersion(selected.value.id, payload)
    versionDialog.value = false
    await loadResumes(selected.value.id)
    ElMessage.success('新版本已创建，历史版本未被覆盖')
  } catch (error) {
    if (error instanceof SyntaxError) ElMessage.error('主内容或区块内容不是有效的 JSON')
    else ElMessage.error(apiErrorMessage(error))
  }
}

async function viewVersion(id) {
  try {
    const [versionResult, metricResult] = await Promise.all([getResumeVersion(id), getResumeVersionMetrics(id)])
    activeVersion.value = versionResult.data.data
    versionMetrics.value = metricResult.data.data
    versionDrawer.value = true
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

async function setFlag(type) {
  if (!selected.value) return
  try {
    if (type === 'master') await setMasterResume(selected.value.id)
    else await setDefaultResume(selected.value.id)
    await loadResumes(selected.value.id)
    ElMessage.success(type === 'master' ? '已设为主简历' : '已设为默认简历')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

async function removeResume() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(`确认归档并删除“${selected.value.name}”？历史版本不会物理删除。`, '删除确认', { type: 'warning' })
    await deleteResume(selected.value.id)
    await loadResumes()
    ElMessage.success('简历已逻辑删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error))
  }
}

function emptyResume() {
  return { name: '', targetRole: '', master: false, defaultResume: false, description: '', status: 'ACTIVE' }
}

function emptyVersion() {
  return { versionName: '', contentText: '{}', renderedText: '', sections: [] }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}
</script>

<template>
  <section v-loading="loading" class="page-section resume-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow accent">结构化 · 可追溯版本</p>
        <h1>简历中心</h1>
        <p>简历代表一个求职方向，版本是不可变的事实快照；每次修改都会创建新版本。</p>
      </div>
      <el-button type="primary" @click="openCreate">创建简历</el-button>
    </div>

    <div class="resume-workspace">
      <aside class="surface resume-list-panel">
        <div class="panel-title"><span>简历列表</span><strong>{{ resumes.length }}</strong></div>
        <button v-for="item in resumes" :key="item.id" class="resume-list-item" :class="{ active: selected?.id === item.id }" @click="selectResume(item.id)">
          <div><strong>{{ item.name }}</strong><p>{{ item.targetRole || '未设置目标岗位' }}</p></div>
          <div class="tag-row"><span v-if="item.master" class="plain-tag">主简历</span><span v-if="item.defaultResume" class="plain-tag accent-tag">默认</span></div>
          <small>{{ item.versionCount }} versions</small>
        </button>
        <div v-if="!resumes.length" class="empty-state compact">创建第一份主简历，作为后续 AI 简历优化的真实事实来源。</div>
      </aside>

      <main class="surface resume-detail-panel">
        <template v-if="selected">
          <div class="resume-detail-heading">
            <div><div class="tag-row"><span v-if="selected.master" class="plain-tag">主简历</span><span v-if="selected.defaultResume" class="plain-tag accent-tag">默认</span><span class="plain-tag">{{ displayText(selected.status) }}</span></div><h2>{{ selected.name }}</h2><p>{{ selected.targetRole || '未设置目标岗位' }}</p></div>
            <el-dropdown trigger="click"><button class="more-button">•••</button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openEdit">编辑信息</el-dropdown-item><el-dropdown-item :disabled="selected.defaultResume" @click="setFlag('default')">设为默认</el-dropdown-item><el-dropdown-item :disabled="selected.master" @click="setFlag('master')">设为主简历</el-dropdown-item><el-dropdown-item divided :disabled="selected.master" @click="removeResume">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          </div>
          <p class="resume-description">{{ selected.description || '暂无描述。可以记录这份简历的侧重点与适用岗位。' }}</p>
          <div class="detail-facts"><span>当前版本<strong>{{ selected.currentVersionNumber ? `v${selected.currentVersionNumber}` : '—' }}</strong></span><span>版本总数<strong>{{ selected.versionCount }}</strong></span><span>最后更新<strong>{{ formatTime(selected.updatedAt) }}</strong></span></div>
          <div class="truth-boundary"><i>✓</i><div><strong>事实边界已启用</strong><p>阶段 1 仅保存你手工确认的结构化内容；历史版本不可覆盖。</p></div></div>
          <div class="detail-actions"><el-button type="primary" @click="openVersion">创建新版本</el-button><el-button :disabled="!selected.currentVersionId" @click="viewVersion(selected.currentVersionId)">查看当前版本</el-button></div>
        </template>
        <div v-else class="empty-state centered">请从左侧创建或选择一份简历</div>
      </main>

      <aside class="surface version-panel">
        <div class="panel-title"><span>版本历史</span><strong>{{ selectedDetail?.versions?.length || 0 }}</strong></div>
        <div v-if="!selectedDetail?.versions?.length" class="empty-state compact">尚无版本。创建 v1 后内容会永久保留。</div>
        <button v-for="item in selectedDetail?.versions || []" :key="item.id" class="version-item" @click="viewVersion(item.id)">
          <span class="version-number">v{{ item.versionNumber }}</span>
          <div><strong>{{ item.versionName }}</strong><p>{{ formatTime(item.createdAt) }}</p></div>
          <span v-if="item.current" class="current-dot">当前</span>
        </button>
      </aside>
    </div>

    <el-dialog v-model="resumeDialog" :title="editingResume ? '编辑简历' : '创建简历'" width="560px">
      <el-form-item label="名称"><el-input v-model="resumeForm.name" placeholder="Java 后端简历" /></el-form-item><el-form-item label="目标岗位"><el-input v-model="resumeForm.targetRole" placeholder="Java 后端开发工程师" /></el-form-item><el-form-item label="状态"><el-select v-model="resumeForm.status"><el-option label="启用" value="ACTIVE" /><el-option label="草稿" value="DRAFT" /><el-option label="已归档" value="ARCHIVED" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="resumeForm.description" type="textarea" :rows="3" /></el-form-item><div v-if="!editingResume" class="switch-row"><el-checkbox v-model="resumeForm.master">设为主简历</el-checkbox><el-checkbox v-model="resumeForm.defaultResume">设为默认</el-checkbox></div>
      <template #footer><el-button @click="resumeDialog=false">取消</el-button><el-button type="primary" @click="saveResume">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="versionDialog" title="创建不可变简历版本" width="760px" top="5vh">
      <el-alert title="保存后不可覆盖；需要修改时请创建下一个版本。" type="info" :closable="false" />
      <el-form-item label="版本名称"><el-input v-model="versionForm.versionName" /></el-form-item>
      <el-form-item label="结构化主内容（JSON 对象）"><el-input v-model="versionForm.contentText" type="textarea" :rows="7" class="code-input" /></el-form-item>
      <el-form-item label="可读文本"><el-input v-model="versionForm.renderedText" type="textarea" :rows="4" placeholder="可选：便于快速查看的纯文本内容" /></el-form-item>
      <div class="section-toolbar"><p>结构化简历区块</p><el-button @click="addSection">添加区块</el-button></div>
      <div v-for="(section, index) in versionForm.sections" :key="index" class="section-editor">
        <el-select v-model="section.sectionType"><el-option v-for="type in sectionTypes" :key="type" :label="displayText(type)" :value="type" /></el-select>
        <el-input v-model="section.contentText" type="textarea" :rows="3" class="code-input" />
        <button class="danger text-button" @click="removeSection(index)">移除</button>
      </div>
      <template #footer><el-button @click="versionDialog=false">取消</el-button><el-button type="primary" @click="saveVersion">创建版本</el-button></template>
    </el-dialog>

    <el-drawer v-model="versionDrawer" size="55%" :title="activeVersion ? `简历版本 v${activeVersion.versionNumber}` : '简历版本'">
      <template v-if="activeVersion">
        <div class="version-meta"><span>{{ activeVersion.versionName }}</span><span>{{ displayText(activeVersion.sourceType) }}</span><span>{{ formatTime(activeVersion.createdAt) }}</span><span v-if="activeVersion.current">当前</span></div>
        <div v-if="activeVersion.truthCheckStatus" class="truth-boundary"><i>✓</i><div><strong>{{ displayText(activeVersion.truthCheckStatus) }}</strong><p>父版本 {{ activeVersion.parentVersionId }} · 岗位 {{ activeVersion.tailoredForJobId }} · {{ activeVersion.promptVersion }}</p></div></div>
        <div v-if="versionMetrics" class="detail-facts"><span>队列使用次数<strong>{{ versionMetrics.queueUseCount }}</strong></span><span>投递次数<strong>{{ versionMetrics.applicationCount }}</strong></span><span>回复次数<strong>{{ versionMetrics.replyCount }}</strong></span></div>
        <h3>可读文本</h3><pre class="rendered-text">{{ activeVersion.renderedText || '未填写可读文本' }}</pre>
        <h3>结构化内容</h3><pre class="json-view">{{ JSON.stringify(activeVersion.content, null, 2) }}</pre>
        <h3>简历区块</h3><div v-for="section in activeVersion.sections" :key="section.id" class="version-section"><strong>{{ displayText(section.sectionType) }}</strong><pre class="json-view small">{{ JSON.stringify(section.content, null, 2) }}</pre></div>
        <p class="hash-line">SHA-256 · {{ activeVersion.contentHash }}</p>
      </template>
    </el-drawer>
  </section>
</template>
