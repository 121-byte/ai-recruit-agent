import request from './request'

export function listUsers(params = {}) {
  return request.get('/admin/users', { params })
}
export function listRoles() {
  return request.get('/admin/users/roles')
}
export function createUser(payload) {
  return request.post('/admin/users', payload)
}
export function updateUser(userId, payload) {
  return request.put(`/admin/users/${userId}`, payload)
}
export function deleteUser(userId) {
  return request.delete(`/admin/users/${userId}`)
}
export function updateUserRoles(userId, roles) {
  return request.put(`/admin/users/${userId}/roles`, { roles })
}
