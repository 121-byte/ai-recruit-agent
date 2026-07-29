import request from './request'

export function dashboardStats() {
  return request.get('/dashboard/stats')
}
export function dashboardTracesBySession(sessionId) {
  return request.get(`/dashboard/traces/session/${sessionId}`)
}
export function dashboardTracesSummary() {
  return request.get('/dashboard/traces/summary')
}
export function dashboardToolStats() {
  return request.get('/dashboard/traces/tool-stats')
}
export function dashboardFunnel() {
  return request.get('/dashboard/funnel')
}
export function dashboardOutreachKanban() {
  return request.get('/dashboard/outreach-kanban')
}
export function dashboardReportOverview() {
  return request.get('/dashboard/report-overview')
}
export function dashboardCostSummary(sessionId) {
  return request.get(`/dashboard/cost-summary/${sessionId}`)
}
export function dashboardAgentMetrics() {
  return request.get('/dashboard/agent-metrics')
}
