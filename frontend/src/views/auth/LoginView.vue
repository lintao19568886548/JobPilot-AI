<script setup>
import { nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from '../../utils/elementFeedback.js'
import { useAuthStore } from '../../stores/auth.js'
import { apiErrorMessage } from '../../api/http.js'
import { useSettingsStore } from '../../stores/settings.js'

const router = useRouter()
const auth = useAuthStore()
const settings = useSettingsStore()
const formRef = ref()
const loginInputRef = ref()
const passwordInputRef = ref()
const form = reactive({ login: '', password: '' })
const rules = {
  login: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  syncBrowserAutofill()
  await nextTick()
  if (!(await formRef.value.validate().catch(() => false))) return
  try {
    await auth.login(form)
    await settings.load()
    ElMessage.success('欢迎回来')
    router.replace(settings.defaultLandingPage)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '登录失败'))
  }
}

function syncBrowserAutofill() {
  const loginValue = loginInputRef.value?.input?.value?.trim()
  const passwordValue = passwordInputRef.value?.input?.value
  if (loginValue) form.login = loginValue
  if (passwordValue) form.password = passwordValue
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <div class="story-content">
        <div class="brand light"><div class="brand-mark">JP</div><strong>JobPilot AI</strong></div>
        <p class="eyebrow">个人职业发展系统</p>
        <h1>把求职信息整理成<br>可持续优化的系统。</h1>
        <p class="story-copy">系统已连接真实 MySQL 与 Redis。你的档案、技能与简历版本会被持久保存，不使用演示岗位数据。</p>
        <div class="story-status"><i /> 本地优先 · 人工确认</div>
      </div>
    </section>
    <section class="login-panel">
      <div class="login-form-wrap">
        <p class="eyebrow accent">JOBPILOT 求职工作区</p>
        <h2>登录到你的求职中控台</h2>
        <p class="muted">使用通过环境变量初始化的本地账号。</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名或邮箱" prop="login">
            <el-input ref="loginInputRef" v-model="form.login" size="large" autocomplete="username" placeholder="local_admin" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input ref="passwordInputRef" v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="••••••••••••" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="auth.loading" class="full-button" @click="submit">进入 JobPilot</el-button>
        </el-form>
        <p class="security-note">凭据仅用于本地后端登录；浏览器会话结束后登录令牌会自动清除。</p>
      </div>
    </section>
  </div>
</template>
