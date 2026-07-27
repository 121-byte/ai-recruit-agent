import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// Pinia Composition API 风格（§12.2）
export const useAuthStore = defineStore('auth', () => {
  // token / user 持久化到 localStorage
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const username = computed(() => user.value?.username || user.value?.name || '')

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

  return { token, user, isAuthenticated, role, username, login, logout }
})
