import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// Pinia Composition API 风格（§12.2）
export const useAuthStore = defineStore('auth', () => {
  // token / user 持久化到 localStorage
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!token.value)
  // 后端登录返回 user.roles (复数数组, 如 ["HR"]/["OPS"]), 一切显隐都基于它
  const roles = computed(() => user.value?.roles || [])
  const role = computed(() => roles.value[0] || '')   // 主角色, 用于标签
  const username = computed(() => user.value?.username || user.value?.name || '')

  // 是否拥有指定角色之一; 不传参视为不限制 (用于路由/菜单显隐)
  function hasAnyRole(...codes) {
    if (!codes || codes.length === 0) return true
    return codes.some((c) => roles.value.includes(c))
  }

  function login(tokenValue, userInfo) {
    token.value = tokenValue
    user.value = userInfo
    localStorage.setItem('token', tokenValue)
    localStorage.setItem('user', JSON.stringify(userInfo))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isAuthenticated, roles, role, username, hasAnyRole, login, logout }
})
