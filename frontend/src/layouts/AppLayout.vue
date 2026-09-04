<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import { useSettingsStore } from '../stores/settings.js'
import GlobalSearch from '../components/GlobalSearch.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const settings = useSettingsStore()

const activePath = computed(() => route.path)

const primaryNav = [
  { path: '/dashboard', label: '工作台', icon: '◫' },
  { path: '/setup', label: '初始化中心', icon: '✓' },
  { path: '/jobs', label: '岗位中心', icon: '⌕' },
  { path: '/recommendations', label: 'AI 岗位推荐', icon: '◇' },
  { path: '/applications', label: '申请中心', icon: '↗' },
  { path: '/interviews', label: '面试中心', icon: '◉' },
  { path: '/offers', label: 'Offer 中心', icon: '◆' },
  { path: '/analytics', label: '数据分析', icon: '▥' },
  { path: '/learning', label: '学习中心', icon: '∿' },
  { path: '/automation-center', label: '安全自动化', icon: '⌁' },
  { path: '/operations', label: '系统运营', icon: '◌' },
  { path: '/ai-studio', label: 'AI 工作室', icon: '✦' },
  { path: '/extension', label: '浏览器扩展', icon: '⊕' },
  { path: '/candidate', label: '候选人档案', icon: '◎' },
  { path: '/resumes', label: '简历中心', icon: '▤' },
  { path: '/settings', label: '设置', icon: '⚙' }
]

onMounted(async () => {
  if (!auth.user && !(await auth.restore())) return
  if (!settings.overview) await settings.load().catch(() => null)
})

async function signOut() {
  await auth.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="app-shell" :class="{ 'compact-mode': settings.compactMode }">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">JP</div>
        <div>
          <strong>JobPilot</strong>
          <span>AI 求职工作区</span>
        </div>
      </div>

      <nav class="nav-group" aria-label="主导航">
        <router-link
          v-for="item in primaryNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: activePath === item.path }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="avatar">{{ (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase() }}</div>
        <div class="user-meta">
          <strong>{{ auth.user?.displayName || auth.user?.username || '本地用户' }}</strong>
          <span>本地工作区</span>
        </div>
        <button class="icon-button" title="退出登录" @click="signOut">↗</button>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumb">
          <span>JobPilot AI</span>
          <span>/</span>
          <strong>{{ route.meta.title || primaryNav.find((item) => item.path === route.path)?.label }}</strong>
        </div>
        <GlobalSearch />
        <div class="phase-badge"><i /> 阶段 15 · 中文界面</div>
      </header>
      <div class="page-container">
        <router-view />
      </div>
    </main>
  </div>
</template>
