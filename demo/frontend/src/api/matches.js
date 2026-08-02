import request from './request'

export function runMatch(jobId, payload = {}) {
  return request.post(`/matches/job/${jobId}/run`, payload)
}
export function getMatchTask(jobId) {
  return request.get(`/matches/job/${jobId}/task`)
}
export function getMatchesByJob(jobId) {
  return request.get(`/matches/job/${jobId}`)
}
export function getMatch(matchId) {
  return request.get(`/matches/${matchId}`)
}
export function matchFeedback(matchId, payload) {
  return request.post(`/matches/${matchId}/feedback`, payload)
}
export function createMatch() {
  return request.post('/matches')
}
// 兼容旧名
export function matchCandidates(jobId) {
  return getMatchesByJob(jobId)
}
export function getMatches(jobId) {
  return getMatchesByJob(jobId)
}
