import request from './request'

// SSE 订阅端点（fetch/EventSource 调用）
export function getEventSubscribeUrl(userId) {
  return `/api/events/subscribe/${userId}`
}
export function listActiveEvents() {
  return request.get('/events/active')
}
