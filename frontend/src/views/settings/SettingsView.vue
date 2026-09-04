<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword, getSessions, revokeSession } from '../../api/auth.js'
import { apiErrorMessage } from '../../api/http.js'
import { useAuthStore } from '../../stores/auth.js'
import { useSettingsStore } from '../../stores/settings.js'
import { displayText } from '../../utils/displayText.js'
import { ElMessage, ElMessageBox } from '../../utils/elementFeedback.js'

const router = useRouter()
const auth = useAuthStore()
const settings = useSettingsStore()
const loading = ref(true)
const accountSaving = ref(false)
const passwordSaving = ref(false)
const sessions = ref([])
const accountForm = reactive({ displayName: '', email: '', timezone: 'Asia/Shanghai', locale: 'zh-CN', version: 0 })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [overview, sessionResponse] = await Promise.all([
      settings.load(),
      getSessions()
    ])
    Object.assign(accountForm, {
      displayName: overview.account.displayName || '',
      email: overview.account.email || '',
      timezone: overview.account.timezone,
      locale: overview.account.locale,
      version: overview.account.version
    })
    sessions.value = sessionResponse.data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '设置加载失败'))
  } finally {
    loading.value = false
  }
}

async function saveAccount() {
  accountSaving.value = true
  try {
    const account = await settings.updateAccount(accountForm)
    accountForm.version = account.version
    auth.user = { ...auth.user, ...account }
    ElMessage.success('账户资料已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '账户资料保存失败'))
  } finally {
    accountSaving.value = false
  }
}

async function setPreference(group, key, value) {
  try {
    await settings.updatePreference(group, key, value)
    ElMessage.success('偏好设置已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '偏好设置保存失败'))
    await settings.load().catch(() => null)
  }
}

async function removeSession(session) {
  try {
    await ElMessageBox.confirm(
      session.current ? '撤销当前会话后需要重新登录，是否继续？' : `撤销 ${displayText(session.clientLabel)} 会话？`,
      '撤销会话',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' }
    )
    await revokeSession(session.id)
    if (session.current) {
      auth.clearSession()
      router.replace('/login')
      return
    }
    sessions.value = (await getSessions()).data.data
    ElMessage.success('会话已撤销')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '会话撤销失败'))
  }
}

async function savePassword() {
  if (passwordForm.newPassword.length < 12) {
    ElMessage.warning('新密码至少需要 12 个字符')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    const { data } = await changePassword(passwordForm)
    ElMessage.success(`密码已修改，已撤销 ${data.data.revokedSessions} 个网页会话`)
    auth.clearSession()
    router.replace('/login')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '密码修改失败'))
  } finally {
    passwordSaving.value = false
  }
}

function setting(group, key, fallback) {
  return settings.overview?.preferences?.find((item) => item.group === group && item.key === key)?.value ?? fallback
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}
</script>

<template>
  <section v-loading="loading" class="page-section settings-page">
    <div class="page-heading">
      <div><p class="eyebrow accent">阶段 14 · 账户安全</p><h1>设置</h1><p>管理账户资料、工作区偏好、密码与当前网页会话。所有修改均持久保存。</p></div>
    </div>

    <div v-if="settings.overview" class="settings-layout">
      <div class="settings-main">
        <section class="surface settings-section">
          <div class="section-heading"><div><span class="section-kicker">账户</span><h2>账户资料</h2></div><span class="status-tag">{{ displayText(settings.account.status) }}</span></div>
          <div class="form-grid two">
            <el-form-item label="用户名"><el-input :model-value="settings.account.username" disabled /></el-form-item>
            <el-form-item label="显示名称"><el-input v-model="accountForm.displayName" maxlength="100" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="accountForm.email" type="email" /></el-form-item>
            <el-form-item label="时区">
              <el-select v-model="accountForm.timezone"><el-option label="Asia/Shanghai" value="Asia/Shanghai" /><el-option label="UTC" value="UTC" /><el-option label="America/New_York" value="America/New_York" /><el-option label="Europe/London" value="Europe/London" /></el-select>
            </el-form-item>
            <el-form-item label="界面语言">
              <el-select v-model="accountForm.locale"><el-option label="简体中文" value="zh-CN" /><el-option label="英语" value="en-US" /></el-select>
            </el-form-item>
          </div>
          <el-button type="primary" :loading="accountSaving" @click="saveAccount">保存账户资料</el-button>
        </section>

        <section class="surface settings-section">
          <div class="section-heading"><div><span class="section-kicker">工作区</span><h2>工作区偏好</h2></div></div>
          <div class="setting-row">
            <div><strong>登录后默认页面</strong><span>下一次登录成功后自动进入所选工作区。</span></div>
            <el-select :model-value="setting('workspace', 'defaultLandingPage', '/dashboard')" @change="setPreference('workspace', 'defaultLandingPage', $event)">
              <el-option label="工作台" value="/dashboard" /><el-option label="初始化中心" value="/setup" /><el-option label="岗位中心" value="/jobs" /><el-option label="AI 岗位推荐" value="/recommendations" /><el-option label="申请中心" value="/applications" />
            </el-select>
          </div>
          <div class="setting-row"><div><strong>紧凑布局</strong><span>缩小工作区边距与信息块间距。</span></div><el-switch :model-value="setting('workspace', 'compactMode', false)" @change="setPreference('workspace', 'compactMode', $event)" /></div>
          <div class="setting-row"><div><strong>工作台数据质量提醒</strong><span>显示初始化就绪度与质量问题入口。</span></div><el-switch :model-value="setting('onboarding', 'showDashboardBanner', true)" @change="setPreference('onboarding', 'showDashboardBanner', $event)" /></div>
        </section>

        <section class="surface settings-section">
          <div class="section-heading"><div><span class="section-kicker">会话</span><h2>活动网页会话</h2></div><strong>{{ sessions.length }}</strong></div>
          <div v-for="session in sessions" :key="session.id" class="session-row">
            <div class="session-mark">{{ session.clientType === 'CLI' ? '>_' : '◎' }}</div>
            <div><strong>{{ displayText(session.clientLabel) }} <span v-if="session.current" class="current-label">当前</span></strong><span>{{ session.ipMasked || 'IP 未记录' }} · 最近使用 {{ formatTime(session.lastUsedAt) }}</span><small>到期 {{ formatTime(session.expiresAt) }}</small></div>
            <el-button plain type="danger" @click="removeSession(session)">撤销</el-button>
          </div>
          <p v-if="!sessions.length" class="empty-state compact">没有活动网页会话。</p>
          <p class="boundary-note">浏览器扩展配对独立管理；修改密码不会静默删除已授权设备，可在“浏览器扩展”页面显式撤销。</p>
        </section>
      </div>

      <aside class="settings-side">
        <section class="surface settings-section password-section">
          <span class="section-kicker">安全</span><h2>修改密码</h2><p>至少 12 个字符。成功后全部网页会话立即失效。</p>
          <el-form label-position="top">
            <el-form-item label="当前密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
            <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
            <el-button class="full-button" type="primary" :loading="passwordSaving" @click="savePassword">修改密码并退出</el-button>
          </el-form>
          <dl class="security-facts"><div><dt>上次修改</dt><dd>{{ formatTime(settings.overview.security.passwordChangedAt) }}</dd></div><div><dt>会话策略</dt><dd>Redis 即时撤销</dd></div><div><dt>密码存储</dt><dd>BCrypt</dd></div></dl>
        </section>
      </aside>
    </div>
  </section>
</template>
