import request from './request'

export function listInterviews(params = {}) {
  return request.get('/interviews', { params })
}
export function createInterview(payload) {
  return request.post('/interviews', payload)
}
export function updateInterviewStatus(interviewId, status) {
  return request.put(`/interviews/${interviewId}/status`, { status })
}
export function getInterview(interviewId) {
  return request.get(`/interviews/${interviewId}`)
}
export function getInterviewsByJob(jobId) {
  return request.get(`/interviews/job/${jobId}`)
}
export function generateInterviewQuestions(interviewId) {
  return request.post(`/interviews/${interviewId}/questions/generate`)
}
export function listInterviewQuestions(interviewId) {
  return request.get(`/interviews/${interviewId}/questions`)
}
export function adoptInterviewQuestion(interviewId, questionId) {
  return request.put(`/interviews/${interviewId}/questions/${questionId}/adopt`)
}
// SSE 端点（fetch 调用）
export function getInterviewStreamUrl(interviewId) {
  return `/api/interviews/${interviewId}/stream`
}
