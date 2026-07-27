import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { requiresAuth: true, title: '仪表盘' }
  },
  {
    path: '/chat',
    name: 'AgentChat',
    component: () => import('@/views/AgentChatView.vue'),
    meta: { requiresAuth: true, title: 'Agent 对话' }
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('@/views/JobsView.vue'),
    meta: { requiresAuth: true, title: '岗位管理' }
  },
  {
    path: '/resumes',
    name: 'Resumes',
    component: () => import('@/views/ResumesView.vue'),
    meta: { requiresAuth: true, title: '简历管理' }
  },
  {
    path: '/matches',
    name: 'Matches',
    component: () => import('@/views/MatchesView.vue'),
    meta: { requiresAuth: true, title: '候选人匹配' }
  },
  {
    path: '/interviews',
    name: 'Interviews',
    component: () => import('@/views/InterviewsView.vue'),
    meta: { requiresAuth: true, title: '面试管理' }
  },
  {
    path: '/interview-agent',
    name: 'InterviewAgent',
    component: () => import('@/views/InterviewAgentView.vue'),
    meta: { requiresAuth: true, title: 'AI 面试官' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未认证跳 /login（§12.1）
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
  } else {
    // 已登录访问 /login 跳首页
    if (to.path === '/login' && authStore.isAuthenticated) {
      next('/')
    } else {
      next()
    }
  }
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - AI 招聘系统`
  }
})

export default router
