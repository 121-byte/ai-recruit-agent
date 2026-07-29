import request from './request'

export function createEvaluationSample(payload) {
  return request.post('/evaluation/samples', payload)
}
export function listEvaluationSamples(params = {}) {
  return request.get('/evaluation/samples', { params })
}
export function runEvaluation() {
  return request.post('/evaluation/run')
}
export function runEvaluationByCategory(category) {
  return request.post(`/evaluation/run/${category}`)
}
export function listEvaluationHistory(params = {}) {
  return request.get('/evaluation/history', { params })
}
