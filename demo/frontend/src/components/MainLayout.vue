<template>
  <div class="app-shell">
    <!-- ===== Top Navigation ===== -->
    <header class="top-nav">
      <div class="top-nav-left">
        <div class="anim-logo" @click="router.push('/')">
          <svg viewBox="0 0 38 38" fill="none">
            <circle class="logo-glow-ring" cx="19" cy="19" r="17" stroke="var(--accent)" stroke-width="1.2" stroke-linecap="round" stroke-dasharray="24 83" opacity="0.5" />
            <circle class="logo-mid-ring" cx="19" cy="19" r="13.5" stroke="var(--fg-2)" stroke-width="0.8" stroke-linecap="round" stroke-dasharray="18 67" opacity="0.4" />
            <circle class="logo-pulse" cx="19" cy="19" r="12" fill="var(--accent)" opacity="0.10" />
            <circle cx="19" cy="19" r="5" fill="var(--accent)" />
            <text class="logo-text" x="19" y="20.5" text-anchor="middle" fill="var(--accent-on)" font-size="8" font-weight="800" font-family="Inter,system-ui,sans-serif">AI</text>
            <circle class="logo-dot logo-dot-1" cx="28" cy="10" r="1.8" fill="var(--accent)" opacity="0" />
            <circle class="logo-dot logo-dot-2" cx="10" cy="28" r="1.5" fill="var(--fg-2)" opacity="0" />
            <circle class="logo-dot logo-dot-3" cx="30" cy="27" r="1.3" fill="var(--accent)" opacity="0" />
          </svg>
        </div>
        <span class="brand-text">AI 招聘</span>
      </div>

      <nav class="top-nav-center" aria-label="主导航">
        <div
          v-for="item in visibleNavItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="go(item.path)"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          {{ item.label }}
          <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
        </div>
      </nav>

      <div class="top-nav-right">
        <button class="icon-btn" title="通知" aria-label="通知">
          <svg viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span class="notif-dot"></span>
        </button>
        <button class="theme-btn" @click="toggle" :title="isDark ? '切换浅色模式' : '切换深色模式'" :aria-label="isDark ? '切换浅色模式' : '切换深色模式'">
          <span>{{ isDark ? '☀️' : '🌙' }}</span>
        </button>
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <div class="top-user">
            <div class="top-user-avatar">{{ avatarText }}</div>
            <span class="name">{{ authStore.username || '用户' }}</span>
          </div>
          <template #overlay>
            <a-menu @click="handleDropdown">
              <a-menu-item key="role" disabled>
                {{ roleLabel }}
                <span style="color:var(--muted);font-size:12px;margin-left:6px">角色</span>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="profile" disabled>
                <span style="color:var(--muted)">(开发中) 个人中心</span>
              </a-menu-item>
              <a-menu-item key="logout" style="color:var(--danger)">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </header>

    <!-- ===== Main ===== -->
    <main class="main-area">
      <header class="page-topbar">
        <h2>{{ currentTitle }}</h2>
        <div class="page-topbar-right">
          <button class="icon-btn" title="刷新" aria-label="刷新页面" @click="refreshPage">
            <svg viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          </button>
        </div>
      </header>

      <div class="content-scroll">
        <div class="screen">
          <slot />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isDark, toggle } = useTheme()

const navItems = [
  {
    path: '/', label: '仪表盘',
    icon: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>',
  },
  {
    path: '/jobs', label: '岗位管理', 
    icon: '<svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>',
  },
  {
    path: '/resumes', label: '简历管理', 
    icon: '<svg viewBox="0 0 24 24"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>',
  },
  {
    path: '/matches', label: '智能匹配',
    icon: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="4"/><line x1="4.93" y1="4.93" x2="9.17" y2="9.17"/><line x1="14.83" y1="14.83" x2="19.07" y2="19.07"/></svg>',
  },
  {
    path: '/interviews', label: '面试管理', 
    icon: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
  },
  {
    path: '/interview-agent', label: 'AI 面试官',
    icon: '<svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="3"/><path d="M12 8v3"/></svg>',
  },
  {
    path: '/chat', label: 'Agent 对话',
    icon: '<svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
  },
  {
    path: '/users', label: '用户管理',
    icon: '<svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  },
]

const currentTitle = computed(() => route.meta?.title || 'AI 招聘')

// 路径 -> 允许角色; 未在此映射中的路径对全部角色可见
const routeRoles = {
  '/matches': ['HR'],
  '/interviews': ['HR'],
  '/interview-agent': ['HR'],
  '/users': ['OPS'],
}
const visibleNavItems = computed(() =>
  navItems.filter((i) => {
    const need = routeRoles[i.path]
    return !need || need.some((r) => authStore.roles.includes(r))
  })
)

const roleMap = { HR: '招聘负责人', OPS: '运营人员', ADMIN: '管理员' }
const roleLabel = computed(() =>
  authStore.roles.map((r) => roleMap[r] || r).join(' / ') || '用户'
)

const avatarText = computed(() => (authStore.username || '用').charAt(0).toUpperCase())

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
function go(path) {
  if (path !== route.path) router.push(path)
}
function handleDropdown({ key }) {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
function refreshPage() {
  router.go(0)
}
</script>
