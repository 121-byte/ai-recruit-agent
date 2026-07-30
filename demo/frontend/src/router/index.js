import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/features/identity/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/features/dashboard/views/DashboardView.vue'),
    meta: { requiresAuth: true, title: '仪表盘' }
  },
  {
    path: '/chat',
    name: 'AgentChat',
    component: () => import('@/features/agent/views/AgentChatView.vue'),
    meta: { requiresAuth: true, title: 'Agent 对话' }
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('@/features/job/views/JobsView.vue'),
    meta: { requiresAuth: true, title: '岗位管理' }
  },
  {
    path: '/resumes',
    name: 'Resumes',
    component: () => import('@/features/resume/views/ResumesView.vue'),
    meta: { requiresAuth: true, title: '简历管理' }
  },
  {
    path: '/resumes/:id',
    name: 'ResumeDetail',
    component: () => import('@/features/resume/views/ResumeDetailView.vue'),
    meta: { requiresAuth: true, title: '简历详情' }
  },
  {
    path: '/matches',
    name: 'Matches',
    component: () => import('@/features/match/views/MatchesView.vue'),
    meta: { requiresAuth: true, roles: ['HR'], title: '候选人匹配' }
  },
  {
    path: '/interviews',
    name: 'Interviews',
    component: () => import('@/features/interview/views/InterviewsView.vue'),
    meta: { requiresAuth: true, roles: ['HR'], title: '面试管理' }
  },
  {
    path: '/interview-agent',
    name: 'InterviewAgent',
    component: () => import('@/features/interview/views/InterviewAgentView.vue'),
    meta: { requiresAuth: true, roles: ['HR'], title: 'AI 面试官' }
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('@/features/identity/views/UsersView.vue'),
    meta: { requiresAuth: true, roles: ['OPS'], title: '用户管理' }
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

// 路由守卫：未认证跳 /login（§12.1）+ 角色级守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    // 已登录访问 /login 跳首页
    next('/')
  } else if (to.meta.roles && to.meta.roles.length && !authStore.hasAnyRole(...to.meta.roles)) {
    // 声明了 meta.roles 且当前用户无交集 → 回首页
    next('/')
  } else {
    next()
  }
})

router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - AI 招聘系统`
  }
})

export default router
