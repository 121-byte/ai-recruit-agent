import request from './request'

export function health() {
  return request.get('/health')
}
// 联网搜索（后端 /api/search，复用 WebSearchTool/Tavily；未配置 key 时返回 Mock 结果）
export function webSearch(query) {
  return request.get('/search', { params: { q: query } })
}
