import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '../utils/authTokens.js'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/auth/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/dashboard/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'setup', name: 'setup', component: () => import('../views/onboarding/SetupCenterView.vue'), meta: { title: '初始化中心' } },
      { path: 'jobs', name: 'jobs', component: () => import('../views/job/JobCenterView.vue'), meta: { title: '岗位中心' } },
      { path: 'recommendations', name: 'recommendations', component: () => import('../views/recommendation/RecommendationView.vue'), meta: { title: 'AI 岗位推荐' } },
      { path: 'applications', name: 'applications', component: () => import('../views/application/ApplicationCenterView.vue'), meta: { title: '申请中心' } },
      { path: 'interviews', name: 'interviews', component: () => import('../views/interview/InterviewCenterView.vue'), meta: { title: '面试中心' } },
      { path: 'offers', name: 'offers', component: () => import('../views/offer/OfferCenterView.vue'), meta: { title: 'Offer 中心' } },
      { path: 'analytics', name: 'analytics', component: () => import('../views/analytics/AnalyticsView.vue'), meta: { title: '数据分析' } },
      { path: 'learning', name: 'learning', component: () => import('../views/learning/LearningView.vue'), meta: { title: '学习中心' } },
      { path: 'automation-center', name: 'automation-center', component: () => import('../views/automation/AutomationCenterView.vue'), meta: { title: '安全自动化' } },
      { path: 'operations', name: 'operations', component: () => import('../views/operations/OperationsView.vue'), meta: { title: '系统运营' } },
      { path: 'ai-studio', name: 'ai-studio', component: () => import('../views/tailoring/AiStudioView.vue'), meta: { title: 'AI 工作室' } },
      { path: 'extension', name: 'extension', component: () => import('../views/extension/ExtensionSettingsView.vue'), meta: { title: '浏览器扩展' } },
      { path: 'candidate', name: 'candidate', component: () => import('../views/candidate/CandidateView.vue'), meta: { title: '候选人档案' } },
      { path: 'resumes', name: 'resumes', component: () => import('../views/resume/ResumeCenterView.vue'), meta: { title: '简历中心' } },
      { path: 'settings', name: 'settings', component: () => import('../views/settings/SettingsView.vue'), meta: { title: '设置' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const authenticated = Boolean(getAccessToken())
  if (!to.meta.public && !authenticated) return '/login'
  if (to.name === 'login' && authenticated) return '/dashboard'
  return true
})

export default router
