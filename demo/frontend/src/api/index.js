import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import router from '@/router'

// axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：注入 satoken header（§12.x）
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers['satoken'] = authStore.token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：401 跳 /login
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 兼容后端统一返回结构 { code, msg, data }
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200 || res.code === 0) {
        return res.data !== undefined ? res.data : res
      }
      // 业务错误
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    const msg =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error.message ||
      '网络错误'
    return Promise.reject(new Error(msg))
  }
)

// ===== 封装的 API 方法 =====

// 登录
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

// 获取当前用户信息
export function fetchUserInfo() {
  return request.get('/auth/me')
}

// 会话列表
export function listSessions() {
  return request.get('/agent/sessions')
}

// 新建会话
export function createSession(title = '') {
  return request.post('/agent/sessions', { title })
}

// 删除会话
export function deleteSession(sessionId) {
  return request.delete(`/agent/sessions/${sessionId}`)
}

// 岗位列表
export function listJobs(params = {}) {
  return request.get('/jobs', { params })
}

// 简历列表
export function listResumes(params = {}) {
  return request.get('/resumes', { params })
}

// 候选人匹配
export function matchCandidates(jobId) {
  return request.get(`/jobs/${jobId}/match`)
}

// 获取匹配结果
export function getMatches(jobId) {
  return request.get(`/jobs/${jobId}/matches`)
}

// 面试列表
export function listInterviews(params = {}) {
  return request.get('/interviews', { params })
}

// 仪表盘统计
export function dashboardStats() {
  return request.get('/dashboard/stats')
}

// 联网搜索
export function webSearch(query) {
  return request.get('/search', { params: { q: query } })
}

// 简历上传（占位）
export function uploadResume(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

// 岗位分析
export function analyzeJob(jobId) {
  return request.post(`/jobs/${jobId}/analyze`)
}

// 简历分析
export function analyzeResume(resumeId) {
  return request.post(`/resumes/${resumeId}/analyze`)
}

// 面试出题
export function generateInterviewQuestions(interviewId) {
  return request.post(`/interviews/${interviewId}/questions`)
}

// 生成面试报告
export function generateInterviewReport(interviewId) {
  return request.post(`/interviews/${interviewId}/report`)
}

export default request
