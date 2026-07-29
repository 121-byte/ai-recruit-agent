import request from './request'

export function interviewAgentStart(interviewId, payload) {
  return request.post(`/interview-agent/interviews/${interviewId}/start`, payload)
}
export function interviewAgentAnswer(sessionId, payload) {
  return request.post(`/interview-agent/sessions/${sessionId}/answer`, payload)
}
// SSE 端点（fetch 调用）
export function interviewAgentAnswerStreamUrl(sessionId) {
  return `/api/interview-agent/sessions/${sessionId}/answer/stream`
}
export function interviewAgentEnd(sessionId) {
  return request.post(`/interview-agent/sessions/${sessionId}/end`)
}
export function interviewAgentAssist(interviewId, payload) {
  return request.post(`/interview-agent/interviews/${interviewId}/assist`, payload)
}
export function interviewAgentReport(interviewId) {
  return request.get(`/interview-agent/interviews/${interviewId}/report`)
}
// 兼容旧名
export function generateInterviewReport(interviewId) {
  return interviewAgentReport(interviewId)
}
