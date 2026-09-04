import axios from 'axios'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../utils/authTokens.js'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export const http = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

const refreshClient = axios.create({ baseURL, timeout: 10000 })
let refreshPromise = null

http.interceptors.request.use((config) => {
  const accessToken = getAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  config.headers['X-Trace-Id'] = crypto.randomUUID()
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status !== 401 || original?._retried || original?.url?.includes('/auth/refresh')) {
      return Promise.reject(error)
    }
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      clearTokens()
      window.location.assign('/login')
      return Promise.reject(error)
    }
    original._retried = true
    if (!refreshPromise) {
      refreshPromise = refreshClient.post('/auth/refresh', { refreshToken })
        .then(({ data }) => {
          setTokens(data.data.accessToken, data.data.refreshToken)
          return data.data.accessToken
        })
        .finally(() => {
          refreshPromise = null
        })
    }
    try {
      const accessToken = await refreshPromise
      original.headers.Authorization = `Bearer ${accessToken}`
      return http(original)
    } catch (refreshError) {
      clearTokens()
      window.location.assign('/login')
      return Promise.reject(refreshError)
    }
  }
)

export function apiErrorMessage(error, fallback = '操作失败，请稍后重试') {
  const response = error.response?.data
  if (response?.data?.fieldErrors?.length) {
    return response.data.fieldErrors.map((item) => `${fieldLabel(item.field)}：${translateServerMessage(item.reason)}`).join('；')
  }
  return translateServerMessage(response?.message || error.message || fallback)
}

const serverMessages = {
  'Validation failed': '数据校验失败',
  'Invalid username or password': '用户名或密码错误',
  Unauthorized: '登录状态无效，请重新登录',
  Forbidden: '没有权限执行此操作',
  'Resource not found': '请求的资源不存在',
  'Internal server error': '服务器内部错误',
  'Network Error': '无法连接服务器，请检查服务是否已启动',
  'Request failed': '请求失败',
  'Candidate profile is required before matching': '开始匹配前请先完善候选人档案',
  'Invalid JSON content': 'JSON 内容格式无效',
  'Invalid cursor': '分页游标无效',
  'Company already exists': '该公司已存在',
  'Explicit external submission confirmation is required': '必须明确确认已在外部完成投递',
  'Pairing code is invalid or expired': '配对码无效或已过期',
  'Extension refresh token is invalid or expired': '扩展登录凭证无效或已过期',
  'Extension device is revoked': '扩展设备授权已撤销',
  'User is unavailable': '当前用户不可用'
}

const fields = {
  usernameOrEmail: '用户名或邮箱', password: '密码', email: '邮箱', phone: '电话',
  fullName: '姓名', targetSalaryMin: '最低期望薪资', targetSalaryMax: '最高期望薪资',
  proficiency: '熟练度', years: '使用年限', startDate: '开始日期', endDate: '结束日期',
  graduationYear: '毕业年份', content: '内容', title: '标题', companyName: '公司名称',
  description: '描述', jobUrl: '岗位地址', meetingLink: '会议链接'
}

function fieldLabel(value) { return fields[value] || value }

function translateServerMessage(value) {
  if (!value) return '操作失败，请稍后重试'
  const raw = String(value)
  if (serverMessages[raw]) return serverMessages[raw]
  return raw
    .replace(/must not be blank/gi, '不能为空')
    .replace(/must be a well-formed email address/gi, '必须是有效的邮箱地址')
    .replace(/must be greater than or equal to/gi, '必须大于或等于')
    .replace(/must be less than or equal to/gi, '必须小于或等于')
    .replace(/must be between/gi, '必须介于')
    .replace(/is required/gi, '为必填项')
    .replace(/not found/gi, '不存在')
}
