import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import router from '@/router'

// axios 实例（baseURL /api，satoken 注入，401 跳 /login）
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：注入 satoken header（从 localStorage token 兜底）
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    const token = authStore.token || localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：兼容后端统一返回结构 { code, msg, data }，401 跳 /login
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code >= 200 && res.code < 300) {
        return res.data !== undefined ? res.data : res
      }
      return Promise.reject(new Error(res.message || res.msg || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    const msg =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error.message ||
      '网络错误'
    return Promise.reject(new Error(msg))
  }
)

export default request
