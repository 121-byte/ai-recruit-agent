import request from './request'

export function getTaskStatus(taskId) {
  return request.get(`/tasks/${taskId}/status`)
}
