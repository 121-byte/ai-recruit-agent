import request from './request'

export function listResumes(params = {}) {
  return request.get('/resumes', { params })
}
export function getResume(resumeId) {
  return request.get(`/resumes/${resumeId}`)
}
export function uploadResume(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}
export function updateResume(resumeId, payload) {
  return request.put(`/resumes/${resumeId}`, payload)
}
export function deleteResume(resumeId) {
  return request.delete(`/resumes/${resumeId}`)
}
export function searchResumes(params) {
  return request.get('/resumes/search', { params })
}
export function analyzeResume(resumeId) {
  return request.post(`/resumes/${resumeId}/analyze`)
}
export function compareResumes(resumeIds) {
  return request.post('/resumes/compare', { resumeIds })
}
export function listIntendedPositions() {
  return request.get('/resumes/intended-positions')
}
export function listPositionCategories() {
  return request.get('/resumes/position-categories')
}
export function listEducations() {
  return request.get('/resumes/educations')
}
