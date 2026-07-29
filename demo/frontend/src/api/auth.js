import request from './request'

// 登录（返回 {token, user}）
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

// 当前用户信息
export function fetchUserInfo() {
  return request.get('/auth/me')
}

// 登出
export function logout() {
  return request.post('/auth/logout')
}
