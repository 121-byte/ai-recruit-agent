import request from './request'

export function listJobs(params = {}) {
  return request.get('/jobs', { params })
}
export function getJob(jobId) {
  return request.get(`/jobs/${jobId}`)
}
export function createJob(payload) {
  return request.post('/jobs', payload)
}
export function updateJob(jobId, payload) {
  return request.put(`/jobs/${jobId}`, payload)
}
export function deleteJob(jobId) {
  return request.delete(`/jobs/${jobId}`)
}
export function analyzeJob(jobId) {
  return request.post(`/jobs/${jobId}/analyze`)
}
export function listDepartments() {
  return request.get('/jobs/departments')
}
export function listLevels() {
  return request.get('/jobs/levels')
}
