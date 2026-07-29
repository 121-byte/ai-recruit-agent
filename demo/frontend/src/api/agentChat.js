import request from './request'

// ===== 会话（/api/agent/sessions） =====
export function listSessions() {
  return request.get('/agent/sessions')
}
export function createSession(title = '') {
  return request.post('/agent/sessions', { title })
}
export function deleteSession(sessionId) {
  return request.delete(`/agent/sessions/${sessionId}`)
}
export function listSessionMessages(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/messages`)
}
export function updateSessionTitle(sessionId, title) {
  return request.put(`/agent/sessions/${sessionId}/title`, { title })
}
export function getSessionTokensSummary() {
  return request.get('/agent/sessions/tokens/summary')
}
export function getSessionTokens(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/tokens`)
}
export function exportSession(sessionId) {
  return request.post(`/agent/sessions/${sessionId}/export`)
}

// ===== Agent 对话（/api/agent/chat/*） =====
export function chatConfirm(payload) {
  return request.post('/agent/chat/confirm', payload)
}
export function chatFeedback(payload) {
  return request.post('/agent/chat/feedback', payload)
}
export function chatExplain(payload) {
  return request.post('/agent/chat/explain', payload)
}
