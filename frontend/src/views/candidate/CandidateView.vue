<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'
import {
  createCandidateSkill, createEducation, createExperience, createProject,
  deleteCandidateSkill, deleteEducation, deleteExperience, deleteProject,
  getProfile, listCandidateSkills, listEducations, listExperiences, listProjects, listSkills,
  putProfile, updateCandidateSkill, updateEducation, updateExperience, updateProject
} from '../../api/candidate.js'
import { apiErrorMessage } from '../../api/http.js'
import { displayText } from '../../utils/displayText.js'

const loading = ref(true)
const savingProfile = ref(false)
const activeTab = ref('basic')
const profile = reactive(emptyProfile())
const educations = ref([])
const experiences = ref([])
const projects = ref([])
const skills = ref([])
const catalog = ref([])

const educationDialog = ref(false)
const experienceDialog = ref(false)
const projectDialog = ref(false)
const skillDialog = ref(false)
const editingId = ref(null)

const educationForm = reactive(emptyEducation())
const experienceForm = reactive(emptyExperience())
const projectForm = reactive(emptyProject())
const skillForm = reactive(emptySkill())

const profileSummary = computed(() => [
  ...(profile.targetRoles || []).slice(0, 2),
  profile.currentCity,
  profile.highestEducation ? displayText(profile.highestEducation) : ''
].filter(Boolean))

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [profileResponse, educationResponse, experienceResponse, projectResponse, candidateSkillResponse, skillResponse] = await Promise.all([
      getProfile(), listEducations(), listExperiences(), listProjects(), listCandidateSkills(), listSkills()
    ])
    Object.assign(profile, emptyProfile(), profileResponse.data.data)
    educations.value = educationResponse.data.data
    experiences.value = experienceResponse.data.data
    projects.value = projectResponse.data.data
    skills.value = candidateSkillResponse.data.data
    catalog.value = skillResponse.data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '候选人资料加载失败'))
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (profile.targetSalaryMin != null && profile.targetSalaryMax != null && Number(profile.targetSalaryMin) > Number(profile.targetSalaryMax)) {
    ElMessage.warning('最低期望薪资不能高于最高期望薪资')
    return
  }
  savingProfile.value = true
  try {
    const payload = {
      ...profile,
      graduationYear: profile.graduationYear || null,
      yearsOfExperience: profile.yearsOfExperience === '' ? null : profile.yearsOfExperience,
      targetSalaryMin: profile.targetSalaryMin === '' ? null : profile.targetSalaryMin,
      targetSalaryMax: profile.targetSalaryMax === '' ? null : profile.targetSalaryMax
    }
    delete payload.id
    delete payload.profileCompleteness
    delete payload.version
    delete payload.createdAt
    delete payload.updatedAt
    const { data } = await putProfile(payload)
    Object.assign(profile, data.data)
    ElMessage.success('候选人档案已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    savingProfile.value = false
  }
}

function openEducation(item) {
  editingId.value = item?.id || null
  Object.assign(educationForm, emptyEducation(), item || {})
  educationDialog.value = true
}

async function saveEducation() {
  if (!educationForm.school || !educationForm.degree || !educationForm.major || !educationForm.startDate) {
    ElMessage.warning('请填写学校、学历、专业和开始日期')
    return
  }
  try {
    const action = editingId.value ? updateEducation(editingId.value, educationForm) : createEducation(educationForm)
    await action
    educationDialog.value = false
    await reloadCandidateData()
    ElMessage.success('教育经历已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

function openExperience(item) {
  editingId.value = item?.id || null
  Object.assign(experienceForm, emptyExperience(), item || {})
  experienceForm.technologyText = (item?.technologies || []).join(', ')
  experienceDialog.value = true
}

async function saveExperience() {
  if (!experienceForm.companyName || !experienceForm.role || !experienceForm.startDate) {
    ElMessage.warning('请填写公司、角色和开始日期')
    return
  }
  const payload = {
    ...experienceForm,
    technologies: splitTags(experienceForm.technologyText),
    endDate: experienceForm.currentlyWorking ? null : experienceForm.endDate
  }
  delete payload.technologyText
  try {
    const action = editingId.value ? updateExperience(editingId.value, payload) : createExperience(payload)
    await action
    experienceDialog.value = false
    await reloadCandidateData()
    ElMessage.success('工作/实习经历已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

function openProject(item) {
  editingId.value = item?.id || null
  Object.assign(projectForm, emptyProject(), item || {})
  projectForm.technologyText = (item?.technologies || []).join(', ')
  projectDialog.value = true
}

async function saveProject() {
  if (!projectForm.name || !projectForm.description) {
    ElMessage.warning('请填写项目名称和简介')
    return
  }
  const payload = { ...projectForm, technologies: splitTags(projectForm.technologyText) }
  delete payload.technologyText
  try {
    const action = editingId.value ? updateProject(editingId.value, payload) : createProject(payload)
    await action
    projectDialog.value = false
    await reloadCandidateData()
    ElMessage.success('项目经历已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

function openSkill(item) {
  editingId.value = item?.id || null
  Object.assign(skillForm, emptySkill(), item ? {
    skillId: item.skill.id,
    proficiency: item.proficiency,
    years: item.years,
    lastUsedAt: item.lastUsedAt,
    source: item.source,
    primary: item.primary
  } : {})
  skillDialog.value = true
}

async function saveSkill() {
  if (!editingId.value && !skillForm.skillId) {
    ElMessage.warning('请选择技能')
    return
  }
  try {
    if (editingId.value) {
      const payload = { ...skillForm }
      delete payload.skillId
      await updateCandidateSkill(editingId.value, payload)
    } else {
      await createCandidateSkill(skillForm)
    }
    skillDialog.value = false
    await reloadCandidateData()
    ElMessage.success('技能已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error)) }
}

async function confirmDelete(type, item) {
  try {
    await ElMessageBox.confirm(`确认删除“${item.name || item.school || item.companyName || item.skill?.displayName}”？`, '删除确认', { type: 'warning' })
    const actions = {
      education: deleteEducation,
      experience: deleteExperience,
      project: deleteProject,
      skill: deleteCandidateSkill
    }
    await actions[type](item.id)
    await reloadCandidateData()
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error))
  }
}

async function reloadCandidateData() {
  const [profileResponse, educationResponse, experienceResponse, projectResponse, skillResponse] = await Promise.all([
    getProfile(), listEducations(), listExperiences(), listProjects(), listCandidateSkills()
  ])
  Object.assign(profile, emptyProfile(), profileResponse.data.data)
  educations.value = educationResponse.data.data
  experiences.value = experienceResponse.data.data
  projects.value = projectResponse.data.data
  skills.value = skillResponse.data.data
}

function splitTags(value) {
  return [...new Set((value || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean))]
}

function emptyProfile() {
  return {
    fullName: '', headline: '', phone: '', email: '', currentCity: '', targetCities: [],
    graduationYear: null, highestEducation: '', school: '', major: '', yearsOfExperience: null,
    jobStatus: '', githubUrl: '', personalWebsite: '', summary: '', targetRoles: [],
    targetIndustries: [], targetCompanyTypes: [], targetSalaryMin: null, targetSalaryMax: null,
    salaryCurrency: 'CNY', acceptRemote: false, acceptRelocation: false, profileCompleteness: 0
  }
}

function emptyEducation() {
  return { school: '', degree: '', major: '', startDate: '', endDate: null, graduationYear: null, description: '', sortOrder: 0 }
}

function emptyExperience() {
  return { companyName: '', role: '', employmentType: 'INTERNSHIP', location: '', startDate: '', endDate: null, currentlyWorking: false, description: '', responsibilities: '', achievements: '', technologies: [], technologyText: '', sortOrder: 0 }
}

function emptyProject() {
  return { name: '', role: '', startDate: null, endDate: null, description: '', background: '', responsibilities: '', achievements: '', technologies: [], technologyText: '', repoUrl: '', demoUrl: '', featured: false, sortOrder: 0 }
}

function emptySkill() {
  return { skillId: '', proficiency: 80, years: null, lastUsedAt: null, source: 'USER', primary: false }
}
</script>

<template>
  <section v-loading="loading" class="page-section candidate-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow accent">候选人数字档案</p>
        <h1>候选人档案</h1>
        <p>只保存你确认过的真实经历；后续匹配与简历优化都以这里为事实边界。</p>
      </div>
      <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
    </div>

    <div class="candidate-layout">
      <div class="surface profile-editor">
        <el-tabs v-model="activeTab" class="clean-tabs">
          <el-tab-pane label="基本资料" name="basic">
            <div class="form-grid two">
              <el-form-item label="姓名"><el-input v-model="profile.fullName" maxlength="100" /></el-form-item>
              <el-form-item label="职业标题"><el-input v-model="profile.headline" maxlength="160" placeholder="Java 后端 / AI Agent 应用开发" /></el-form-item>
              <el-form-item label="邮箱"><el-input v-model="profile.email" type="email" /></el-form-item>
              <el-form-item label="电话"><el-input v-model="profile.phone" /></el-form-item>
              <el-form-item label="当前城市"><el-input v-model="profile.currentCity" /></el-form-item>
              <el-form-item label="求职状态">
                <el-select v-model="profile.jobStatus" clearable><el-option label="积极求职" value="ACTIVELY_LOOKING" /><el-option label="开放机会" value="OPEN_TO_OPPORTUNITIES" /><el-option label="暂不考虑" value="NOT_LOOKING" /></el-select>
              </el-form-item>
              <el-form-item label="最高学历"><el-select v-model="profile.highestEducation" clearable allow-create filterable placeholder="请选择或输入"><el-option v-for="item in ['HIGH_SCHOOL','ASSOCIATE','BACHELOR','MASTER_DEGREE','DOCTOR']" :key="item" :label="displayText(item)" :value="item" /></el-select></el-form-item>
              <el-form-item label="毕业年份"><el-input-number v-model="profile.graduationYear" :min="1950" :max="2100" controls-position="right" /></el-form-item>
              <el-form-item label="学校"><el-input v-model="profile.school" /></el-form-item>
              <el-form-item label="专业"><el-input v-model="profile.major" /></el-form-item>
              <el-form-item label="工作年限"><el-input-number v-model="profile.yearsOfExperience" :min="0" :max="80" :step="0.5" controls-position="right" /></el-form-item>
            </div>
            <el-form-item label="个人摘要"><el-input v-model="profile.summary" type="textarea" :rows="5" maxlength="4000" show-word-limit /></el-form-item>
          </el-tab-pane>

          <el-tab-pane label="求职目标" name="target">
            <div class="form-grid two">
              <el-form-item label="目标岗位"><el-select v-model="profile.targetRoles" multiple allow-create filterable default-first-option placeholder="输入并回车"><el-option v-for="item in ['Java 后端开发工程师','Java 开发工程师','Java + AI Agent 开发工程师','AI 应用开发工程师','后端开发工程师','Java 全栈开发工程师']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
              <el-form-item label="目标城市"><el-select v-model="profile.targetCities" multiple allow-create filterable default-first-option placeholder="输入并回车" /></el-form-item>
              <el-form-item label="目标行业"><el-select v-model="profile.targetIndustries" multiple allow-create filterable default-first-option /></el-form-item>
              <el-form-item label="公司类型"><el-select v-model="profile.targetCompanyTypes" multiple allow-create filterable default-first-option /></el-form-item>
              <el-form-item label="最低期望月薪"><el-input-number v-model="profile.targetSalaryMin" :min="0" :max="10000000" :step="1000" controls-position="right" /></el-form-item>
              <el-form-item label="最高期望月薪"><el-input-number v-model="profile.targetSalaryMax" :min="0" :max="10000000" :step="1000" controls-position="right" /></el-form-item>
              <el-form-item label="币种"><el-select v-model="profile.salaryCurrency"><el-option label="CNY" value="CNY" /><el-option label="USD" value="USD" /></el-select></el-form-item>
              <div class="switch-row"><el-checkbox v-model="profile.acceptRemote">接受远程</el-checkbox><el-checkbox v-model="profile.acceptRelocation">接受异地搬迁</el-checkbox></div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="教育背景" name="education">
            <div class="section-toolbar"><p>{{ educations.length }} 条真实教育经历</p><el-button @click="openEducation()">新增教育</el-button></div>
            <div v-if="!educations.length" class="empty-state">尚未添加教育背景</div>
            <div v-for="item in educations" :key="item.id" class="record-row">
              <div><strong>{{ item.school }}</strong><p>{{ item.degree }} · {{ item.major }} · {{ item.startDate }} — {{ item.endDate || '至今' }}</p></div>
              <div class="row-actions"><button @click="openEducation(item)">编辑</button><button class="danger" @click="confirmDelete('education', item)">删除</button></div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="技能" name="skills">
            <div class="section-toolbar"><p>{{ skills.length }} 项技能，熟练度用于后续匹配</p><el-button @click="openSkill()">新增技能</el-button></div>
            <div v-if="!skills.length" class="empty-state">从标准技能库添加你的真实技能</div>
            <div class="skill-list">
              <div v-for="item in skills" :key="item.id" class="skill-row">
                <div class="skill-title"><strong>{{ item.skill.displayName }}</strong><span>{{ displayText(item.skill.category) }}</span><em v-if="item.primary">核心</em></div>
                <el-progress :percentage="item.proficiency" :stroke-width="7" />
                <div class="row-actions"><button @click="openSkill(item)">编辑</button><button class="danger" @click="confirmDelete('skill', item)">删除</button></div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="工作 / 实习" name="experience">
            <div class="section-toolbar"><p>实习、正式工作、兼职与其他经历</p><el-button @click="openExperience()">新增经历</el-button></div>
            <div v-if="!experiences.length" class="empty-state">尚未添加工作或实习经历</div>
            <div v-for="item in experiences" :key="item.id" class="record-row rich">
              <div><strong>{{ item.companyName }} · {{ item.role }}</strong><p>{{ item.employmentType }} · {{ item.startDate }} — {{ item.currentlyWorking ? '至今' : item.endDate }}</p><div class="tag-row"><span v-for="tech in item.technologies" :key="tech" class="plain-tag">{{ tech }}</span></div></div>
              <div class="row-actions"><button @click="openExperience(item)">编辑</button><button class="danger" @click="confirmDelete('experience', item)">删除</button></div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="项目经历" name="projects">
            <div class="section-toolbar"><p>项目技术栈将成为后续岗位匹配的证据</p><el-button @click="openProject()">新增项目</el-button></div>
            <div v-if="!projects.length" class="empty-state">尚未添加项目经历</div>
            <el-collapse class="project-collapse">
              <el-collapse-item v-for="item in projects" :key="item.id" :name="item.id">
                <template #title><div class="project-title"><strong>{{ item.name }}</strong><span>{{ item.role }}</span><em v-if="item.featured">精选</em></div></template>
                <p>{{ item.description }}</p>
                <div class="tag-row"><span v-for="tech in item.technologies" :key="tech" class="plain-tag">{{ tech }}</span></div>
                <div class="record-links"><a v-if="item.repoUrl" :href="item.repoUrl" target="_blank" rel="noreferrer">代码仓库 ↗</a><a v-if="item.demoUrl" :href="item.demoUrl" target="_blank" rel="noreferrer">演示地址 ↗</a></div>
                <div class="row-actions aligned"><button @click="openProject(item)">编辑</button><button class="danger" @click="confirmDelete('project', item)">删除</button></div>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>

          <el-tab-pane label="在线主页" name="online">
            <div class="form-grid two">
              <el-form-item label="GitHub"><el-input v-model="profile.githubUrl" placeholder="https://github.com/..." /></el-form-item>
              <el-form-item label="个人网站"><el-input v-model="profile.personalWebsite" placeholder="https://..." /></el-form-item>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <aside class="surface profile-aside">
        <span class="section-kicker">档案完整度</span>
        <div class="score-ring" :style="{ '--score': `${profile.profileCompleteness * 3.6}deg` }"><strong>{{ profile.profileCompleteness }}%</strong></div>
        <h3>{{ profile.fullName || '待完善姓名' }}</h3>
        <p>{{ profile.headline || '补充职业标题，让求职方向更清晰。' }}</p>
        <div class="tag-row"><span v-for="item in profileSummary" :key="item" class="plain-tag">{{ item }}</span></div>
        <div class="aside-facts"><span>核心技能 <strong>{{ skills.filter((item) => item.primary).length }}</strong></span><span>精选项目 <strong>{{ projects.filter((item) => item.featured).length }}</strong></span><span>简历事实 <strong>用户确认</strong></span></div>
      </aside>
    </div>

    <el-dialog v-model="educationDialog" :title="editingId ? '编辑教育背景' : '新增教育背景'" width="560px">
      <div class="form-grid two"><el-form-item label="学校"><el-input v-model="educationForm.school" /></el-form-item><el-form-item label="学历"><el-input v-model="educationForm.degree" /></el-form-item><el-form-item label="专业"><el-input v-model="educationForm.major" /></el-form-item><el-form-item label="毕业年份"><el-input-number v-model="educationForm.graduationYear" :min="1950" :max="2100" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="educationForm.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="educationForm.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></div><el-form-item label="说明"><el-input v-model="educationForm.description" type="textarea" :rows="3" /></el-form-item>
      <template #footer><el-button @click="educationDialog=false">取消</el-button><el-button type="primary" @click="saveEducation">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="experienceDialog" :title="editingId ? '编辑经历' : '新增经历'" width="680px">
      <div class="form-grid two"><el-form-item label="公司/组织"><el-input v-model="experienceForm.companyName" /></el-form-item><el-form-item label="角色"><el-input v-model="experienceForm.role" /></el-form-item><el-form-item label="类型"><el-select v-model="experienceForm.employmentType"><el-option label="实习" value="INTERNSHIP" /><el-option label="正式工作" value="FULL_TIME" /><el-option label="兼职" value="PART_TIME" /><el-option label="其他" value="OTHER" /></el-select></el-form-item><el-form-item label="地点"><el-input v-model="experienceForm.location" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="experienceForm.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="experienceForm.endDate" type="date" value-format="YYYY-MM-DD" :disabled="experienceForm.currentlyWorking" /></el-form-item></div><el-checkbox v-model="experienceForm.currentlyWorking">当前仍在这里工作</el-checkbox><el-form-item label="技术栈（逗号分隔）"><el-input v-model="experienceForm.technologyText" /></el-form-item><el-form-item label="简介"><el-input v-model="experienceForm.description" type="textarea" :rows="2" /></el-form-item><el-form-item label="职责"><el-input v-model="experienceForm.responsibilities" type="textarea" :rows="3" /></el-form-item><el-form-item label="成果"><el-input v-model="experienceForm.achievements" type="textarea" :rows="3" /></el-form-item>
      <template #footer><el-button @click="experienceDialog=false">取消</el-button><el-button type="primary" @click="saveExperience">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="projectDialog" :title="editingId ? '编辑项目' : '新增项目'" width="720px">
      <div class="form-grid two"><el-form-item label="项目名称"><el-input v-model="projectForm.name" /></el-form-item><el-form-item label="角色"><el-input v-model="projectForm.role" /></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="projectForm.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="projectForm.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="代码仓库"><el-input v-model="projectForm.repoUrl" /></el-form-item><el-form-item label="演示地址"><el-input v-model="projectForm.demoUrl" /></el-form-item></div><el-checkbox v-model="projectForm.featured">设为精选项目</el-checkbox><el-form-item label="技术栈（逗号分隔）"><el-input v-model="projectForm.technologyText" /></el-form-item><el-form-item label="项目简介"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item><el-form-item label="项目背景"><el-input v-model="projectForm.background" type="textarea" :rows="2" /></el-form-item><el-form-item label="职责"><el-input v-model="projectForm.responsibilities" type="textarea" :rows="3" /></el-form-item><el-form-item label="成果"><el-input v-model="projectForm.achievements" type="textarea" :rows="3" /></el-form-item>
      <template #footer><el-button @click="projectDialog=false">取消</el-button><el-button type="primary" @click="saveProject">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="skillDialog" :title="editingId ? '编辑技能' : '新增技能'" width="520px">
      <el-form-item label="标准技能"><el-select v-model="skillForm.skillId" filterable :disabled="Boolean(editingId)" placeholder="搜索技能"><el-option v-for="item in catalog" :key="item.id" :label="`${item.displayName} · ${displayText(item.category)}`" :value="item.id" /></el-select></el-form-item><el-form-item label="熟练度"><el-slider v-model="skillForm.proficiency" :min="0" :max="100" show-input /></el-form-item><div class="form-grid two"><el-form-item label="使用年限"><el-input-number v-model="skillForm.years" :min="0" :max="80" :step="0.5" /></el-form-item><el-form-item label="最近使用"><el-date-picker v-model="skillForm.lastUsedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item></div><el-form-item label="来源"><el-input v-model="skillForm.source" placeholder="用户录入 / 项目 / 工作经历" /></el-form-item><el-checkbox v-model="skillForm.primary">设为核心技能</el-checkbox>
      <template #footer><el-button @click="skillDialog=false">取消</el-button><el-button type="primary" @click="saveSkill">保存</el-button></template>
    </el-dialog>
  </section>
</template>
