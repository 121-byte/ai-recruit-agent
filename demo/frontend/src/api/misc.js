import request from './request'

export function health() {
  return request.get('/health')
}
// 兼容旧名：联网搜索（后端无对应 P2 端点，保留占位）
export function webSearch(query) {
  return request.get('/search', { params: { q: query } })
}
