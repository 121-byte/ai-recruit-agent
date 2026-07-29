import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import router from '@/router'

// axios 实例（§3 重写：baseURL /api，satoken 注入，401 跳 /login）
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：注入 satoken header（从 localStorage token 兜底，§12.x）
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    const token = authStore.token || localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：兼容后端统一返回结构 { code, msg, data }，401 跳 /login
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200 || res.code === 0) {
        return res.data !== undefined ? res.data : res
      }
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

// ===== 认证（/api/auth/*） =====

// 登录（返回 {token, user}）
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

// 当前用户信息（原 /userinfo，后端返回 LoginResponse）
export function fetchUserInfo() {
  return request.get('/auth/me')
}

// 登出
export function logout() {
  return request.post('/auth/logout')
}

// ===== 会话（/api/agent/sessions） =====

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

// 会话消息列表
export function listSessionMessages(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/messages`)
}

// 修改会话标题
export function updateSessionTitle(sessionId, title) {
  return request.put(`/agent/sessions/${sessionId}/title`, { title })
}

// 会话 token 汇总
export function getSessionTokensSummary() {
  return request.get('/agent/sessions/tokens/summary')
}

// 单会话 token 明细
export function getSessionTokens(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/tokens`)
}

// 导出会话
export function exportSession(sessionId) {
  return request.post(`/agent/sessions/${sessionId}/export`, null, {
    responseType: 'blob'
  })
}

// ===== Agent 对话（/api/agent/chat/*） =====

// SSE 流式聊天（fetch 直接调用，这里仅占位；composable useAgentStream 处理流）
// 普通同步聊天
export function chat(payload) {
  return request.post('/agent/chat', payload)
}

// 停止聊天
export function chatStop(sessionId) {
  return request.post('/agent/chat/stop', { sessionId })
}

// HITL 确认
export function chatConfirm(payload) {
  return request.post('/agent/chat/confirm', payload)
}

// 反馈
export function chatFeedback(payload) {
  return request.post('/agent/chat/feedback', payload)
}

// 解释
export function chatExplain(payload) {
  return request.post('/agent/chat/explain', payload)
}

// Agent 岗位分析
export function agentAnalyzeJob(jobId, payload) {
  return request.post(`/agent/jobs/${jobId}/analyze`, payload)
}

// Agent 岗位匹配
export function agentMatchJob(jobId, payload) {
  return request.post(`/agent/jobs/${jobId}/match`, payload)
}

// Agent 面试出题
export function agentInterviewQuestions(interviewId, payload) {
  return request.post(`/agent/interviews/${interviewId}/questions`, payload)
}

// ===== 岗位（/api/jobs） =====

// 岗位列表
export function listJobs(params = {}) {
  return request.get('/jobs', { params })
}

// 岗位详情
export function getJob(jobId) {
  return request.get(`/jobs/${jobId}`)
}

// 新建岗位
export function createJob(payload) {
  return request.post('/jobs', payload)
}

// 更新岗位
export function updateJob(jobId, payload) {
  return request.put(`/jobs/${jobId}`, payload)
}

// 删除岗位
export function deleteJob(jobId) {
  return request.delete(`/jobs/${jobId}`)
}

// 岗位分析（jobs 资源下）
export function analyzeJob(jobId) {
  return request.post(`/jobs/${jobId}/analyze`)
}

// 部门列表（筛选用）
export function listDepartments() {
  return request.get('/jobs/departments')
}

// 职级列表（筛选用）
export function listLevels() {
  return request.get('/jobs/levels')
}

// ===== 简历（/api/resumes） =====

// 简历列表
export function listResumes(params = {}) {
  return request.get('/resumes', { params })
}

// 简历详情
export function getResume(resumeId) {
  return request.get(`/resumes/${resumeId}`)
}

// 简历上传
export function uploadResume(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

// 更新简历
export function updateResume(resumeId, payload) {
  return request.put(`/resumes/${resumeId}`, payload)
}

// 删除简历
export function deleteResume(resumeId) {
  return request.delete(`/resumes/${resumeId}`)
}

// 简历搜索
export function searchResumes(params) {
  return request.get('/resumes/search', { params })
}

// 简历分析（独立路径）
export function analyzeResume(resumeId) {
  return request.post(`/resumes/${resumeId}/analyze`)
}

// 简历对比
export function compareResumes(resumeIds) {
  return request.post('/resumes/compare', { resumeIds })
}

// 意向岗位列表（筛选用）
export function listIntendedPositions() {
  return request.get('/resumes/intended-positions')
}

// 学历列表（筛选用）
export function listEducations() {
  return request.get('/resumes/educations')
}

// ===== 匹配（/api/matches） =====

// 发起匹配任务（原 POST /{jobId}，对齐为 /job/{jobId}/run）
export function runMatch(jobId) {
  return request.post(`/matches/job/${jobId}/run`)
}

// 按岗位获取匹配结果
export function getMatchesByJob(jobId) {
  return request.get(`/matches/job/${jobId}`)
}

// 匹配详情
export function getMatch(matchId) {
  return request.get(`/matches/${matchId}`)
}

// 匹配反馈
export function matchFeedback(matchId, payload) {
  return request.post(`/matches/${matchId}/feedback`, payload)
}

// 创建匹配（无参）
export function createMatch() {
  return request.post('/matches')
}

// 兼容旧名：候选人匹配
export function matchCandidates(jobId) {
  return getMatchesByJob(jobId)
}

// 兼容旧名：获取匹配结果
export function getMatches(jobId) {
  return getMatchesByJob(jobId)
}

// ===== 面试（/api/interviews） =====

// 面试列表
export function listInterviews(params = {}) {
  return request.get('/interviews', { params })
}

// 新建面试
export function createInterview(payload) {
  return request.post('/interviews', payload)
}

// 更新面试状态
export function updateInterviewStatus(interviewId, status) {
  return request.put(`/interviews/${interviewId}/status`, { status })
}

// 面试详情
export function getInterview(interviewId) {
  return request.get(`/interviews/${interviewId}`)
}

// 按岗位获取面试
export function getInterviewsByJob(jobId) {
  return request.get(`/interviews/job/${jobId}`)
}

// 生成面试题目（原 /{id}/questions 对齐为 /{id}/questions/generate）
export function generateInterviewQuestions(interviewId) {
  return request.post(`/interviews/${interviewId}/questions/generate`)
}

// 获取面试题目列表
export function listInterviewQuestions(interviewId) {
  return request.get(`/interviews/${interviewId}/questions`)
}

// 采纳面试题
export function adoptInterviewQuestion(interviewId, questionId) {
  return request.put(`/interviews/${interviewId}/questions/${questionId}/adopt`)
}

// 面试流（SSE 端点，fetch 调用）
export function getInterviewStreamUrl(interviewId) {
  return `/api/interviews/${interviewId}/stream`
}

// ===== AI 面试官（/api/interview-agent） =====

// 启动面试
export function interviewAgentStart(interviewId, payload) {
  return request.post(`/interview-agent/interviews/${interviewId}/start`, payload)
}

// 提交答案
export function interviewAgentAnswer(sessionId, payload) {
  return request.post(`/interview-agent/sessions/${sessionId}/answer`, payload)
}

// 流式提交答案（SSE 端点，fetch 调用）
export function interviewAgentAnswerStreamUrl(sessionId) {
  return `/api/interview-agent/sessions/${sessionId}/answer/stream`
}

// 结束面试
export function interviewAgentEnd(sessionId) {
  return request.post(`/interview-agent/sessions/${sessionId}/end`)
}

// 面试辅助
export function interviewAgentAssist(interviewId, payload) {
  return request.post(`/interview-agent/interviews/${interviewId}/assist`, payload)
}

// 面试报告
export function interviewAgentReport(interviewId) {
  return request.get(`/interview-agent/interviews/${interviewId}/report`)
}

// 兼容旧名：生成面试报告
export function generateInterviewReport(interviewId) {
  return interviewAgentReport(interviewId)
}

// ===== 评估（/api/evaluation） =====

// 新增评估样本
export function createEvaluationSample(payload) {
  return request.post('/evaluation/samples', payload)
}

// 评估样本列表
export function listEvaluationSamples(params = {}) {
  return request.get('/evaluation/samples', { params })
}

// 运行评估（全量）
export function runEvaluation() {
  return request.post('/evaluation/run')
}

// 按分类运行评估
export function runEvaluationByCategory(category) {
  return request.post(`/evaluation/run/${category}`)
}

// 评估历史
export function listEvaluationHistory(params = {}) {
  return request.get('/evaluation/history', { params })
}

// ===== 用户管理（/api/admin/users） =====

// 用户列表
export function listUsers(params = {}) {
  return request.get('/admin/users', { params })
}

// 角色主数据 (供角色多选下拉)
export function listRoles() {
  return request.get('/admin/users/roles')
}

// 新建用户
export function createUser(payload) {
  return request.post('/admin/users', payload)
}

// 更新用户
export function updateUser(userId, payload) {
  return request.put(`/admin/users/${userId}`, payload)
}

// 删除用户
export function deleteUser(userId) {
  return request.delete(`/admin/users/${userId}`)
}

// 分配用户角色 (body { roles: ['HR','OPS'] })
export function updateUserRoles(userId, roles) {
  return request.put(`/admin/users/${userId}/roles`, { roles })
}

// ===== 仪表盘（/api/dashboard） =====

// 仪表盘统计
export function dashboardStats() {
  return request.get('/dashboard/stats')
}

// 会话追踪明细
export function dashboardTracesBySession(sessionId) {
  return request.get(`/dashboard/traces/session/${sessionId}`)
}

// 追踪汇总
export function dashboardTracesSummary() {
  return request.get('/dashboard/traces/summary')
}

// 工具调用统计
export function dashboardToolStats() {
  return request.get('/dashboard/traces/tool-stats')
}

// 漏斗
export function dashboardFunnel() {
  return request.get('/dashboard/funnel')
}

// 外联看板
export function dashboardOutreachKanban() {
  return request.get('/dashboard/outreach-kanban')
}

// 报告概览
export function dashboardReportOverview() {
  return request.get('/dashboard/report-overview')
}

// 会话成本
export function dashboardCostSummary(sessionId) {
  return request.get(`/dashboard/cost-summary/${sessionId}`)
}

// Agent 指标
export function dashboardAgentMetrics() {
  return request.get('/dashboard/agent-metrics')
}

// ===== 任务（/api/tasks） =====

// 任务状态
export function getTaskStatus(taskId) {
  return request.get(`/tasks/${taskId}/status`)
}

// ===== 事件（/api/events） =====

// 订阅事件（SSE 端点，fetch/EventSource 调用）
export function getEventSubscribeUrl(userId) {
  return `/api/events/subscribe/${userId}`
}

// 活跃事件
export function listActiveEvents() {
  return request.get('/events/active')
}

// ===== 健康（/api/health） =====

export function health() {
  return request.get('/health')
}

// 兼容旧名：联网搜索（后端无对应 P2 端点，保留占位）
export function webSearch(query) {
  return request.get('/search', { params: { q: query } })
}

export default request
