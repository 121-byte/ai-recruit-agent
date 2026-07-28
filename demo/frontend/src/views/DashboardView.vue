<template>
  <MainLayout>
    <!-- Skeleton -->
    <div v-if="loading" class="skeleton-row">
      <div v-for="n in 4" :key="n" class="skeleton-card">
        <div class="skeleton"></div>
        <div class="skeleton"></div>
      </div>
    </div>

    <div v-else>
      <div class="welcome-row">
        <div class="welcome-text">
          <h1>{{ greeting }}{{ authStore.username ? '，' + authStore.username : '' }} 👋</h1>
          <p>以下是今天的招聘概况</p>
        </div>
        <div class="date-chip">{{ todayStr }}</div>
      </div>

      <!-- Metric cards -->
      <div class="metric-grid">
        <div
          v-for="card in metricCards"
          :key="card.key"
          class="metric-card"
        >
          <div class="metric-header">
            <span class="metric-label">{{ card.label }}</span>
            <span class="metric-icon" :class="card.key" v-html="card.icon"></span>
          </div>
          <div class="metric-value"><CountUp :target="card.value" /></div>
          <div class="metric-change" :class="{ up: card.trendDir === 'up' }">
            {{ card.trend }}
          </div>
          <svg class="sparkline" :class="card.key" viewBox="0 0 100 28" preserveAspectRatio="none">
            <path class="area" :d="card.area" />
            <path class="line" :d="card.line" />
          </svg>
        </div>
      </div>

      <!-- Content row -->
      <div class="content-row">
        <div class="panel">
          <div class="panel-head">
            <h3>最近活动</h3>
            <span class="panel-link" @click="router.push('/interviews')">查看全部</span>
          </div>
          <div class="panel-body">
            <div
              v-for="(act, i) in activities"
              :key="i"
              class="timeline-item"
            >
              <span class="timeline-dot" :class="act.dotClass || 'accent'"></span>
              <div class="timeline-content">
                <div class="timeline-text" v-html="act.text"></div>
                <div class="timeline-time">{{ act.time }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>接下来面试</h3>
            <span class="panel-link" @click="router.push('/interviews')">全部日程</span>
          </div>
          <div class="panel-body">
            <div
              v-for="(iv, i) in upcomingInterviews"
              :key="i"
              class="interview-item"
            >
              <div class="interview-avatar">{{ iv.avatar }}</div>
              <div class="interview-info">
                <div class="name">{{ iv.name }}</div>
                <div class="detail">{{ iv.detail }}</div>
              </div>
              <span class="interview-badge" :class="iv.badgeClass">{{ iv.badge }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardStats } from '@/api'
import { useAuthStore } from '@/store/auth'
import MainLayout from '@/components/MainLayout.vue'
import CountUp from '@/components/CountUp.vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const stats = ref({})

const ICONS = {
  resume: '<svg viewBox="0 0 24 24"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
  job: '<svg viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>',
  interview: '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
  match: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="4"/><line x1="4.93" y1="4.93" x2="9.17" y2="9.17"/><line x1="14.83" y1="14.83" x2="19.07" y2="19.07"/></svg>',
}

const SPARK = {
  resume: {
    area: 'M0,26 L2,24 L4,20 L8,22 L12,16 L16,18 L20,12 L24,14 L28,8 L32,10 L36,6 L40,8 L44,4 L48,6 L52,2 L56,4 L60,3 L64,5 L68,4 L72,6 L76,5 L80,7 L84,6 L88,8 L92,7 L96,9 L100,8 L100,28 L0,28Z',
    line: 'M0,26 L2,24 L4,20 L8,22 L12,16 L16,18 L20,12 L24,14 L28,8 L32,10 L36,6 L40,8 L44,4 L48,6 L52,2 L56,4 L60,3 L64,5 L68,4 L72,6 L76,5 L80,7 L84,6 L88,8 L92,7 L96,9 L100,8',
  },
  job: {
    area: 'M0,22 L2,20 L4,18 L8,16 L12,14 L16,15 L20,12 L24,10 L28,11 L32,9 L36,8 L40,7 L44,6 L48,5 L52,4 L56,3 L60,4 L64,3 L68,5 L72,4 L76,6 L80,5 L84,7 L88,6 L92,8 L96,7 L100,9 L100,28 L0,28Z',
    line: 'M0,22 L2,20 L4,18 L8,16 L12,14 L16,15 L20,12 L24,10 L28,11 L32,9 L36,8 L40,7 L44,6 L48,5 L52,4 L56,3 L60,4 L64,3 L68,5 L72,4 L76,6 L80,5 L84,7 L88,6 L92,8 L96,7 L100,9',
  },
  interview: {
    area: 'M0,26 L2,25 L4,24 L8,23 L12,22 L16,20 L20,18 L24,16 L28,14 L32,12 L36,10 L40,9 L44,8 L48,7 L52,6 L56,5 L60,4 L64,3 L68,2 L72,4 L76,6 L80,8 L84,7 L88,9 L92,11 L96,13 L100,15 L100,28 L0,28Z',
    line: 'M0,26 L2,25 L4,24 L8,23 L12,22 L16,20 L20,18 L24,16 L28,14 L32,12 L36,10 L40,9 L44,8 L48,7 L52,6 L56,5 L60,4 L64,3 L68,2 L72,4 L76,6 L80,8 L84,7 L88,9 L92,11 L96,13 L100,15',
  },
  match: {
    area: 'M0,20 L2,18 L4,16 L8,14 L12,12 L16,10 L20,8 L24,6 L28,4 L32,2 L36,3 L40,5 L44,4 L48,6 L52,8 L56,7 L60,9 L64,8 L68,10 L72,9 L76,11 L80,10 L84,12 L88,11 L92,13 L96,12 L100,14 L100,28 L0,28Z',
    line: 'M0,20 L2,18 L4,16 L8,14 L12,12 L16,10 L20,8 L24,6 L28,4 L32,2 L36,3 L40,5 L44,4 L48,6 L52,8 L56,7 L60,9 L64,8 L68,10 L72,9 L76,11 L80,10 L84,12 L88,11 L92,13 L96,12 L100,14',
  },
}

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayStr = computed(() => {
  const d = new Date()
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${weekdays[d.getDay()]}`
})

const metricCards = computed(() => [
  {
    key: 'resume', label: '简历总数',
    value: stats.value.resumeCount ?? stats.value.resumes ?? 0,
    trendDir: 'up', trend: stats.value.resumeCount != null ? '↑ 简历库总览' : '↑ 本周新增',
    icon: ICONS.resume, ...SPARK.resume,
  },
  {
    key: 'job', label: '在招岗位',
    value: stats.value.jobCount ?? stats.value.jobs ?? 0,
    trendDir: 'flat', trend: '开放中岗位',
    icon: ICONS.job, ...SPARK.job,
  },
  {
    key: 'interview', label: '面试安排',
    value: stats.value.interviewCount ?? stats.value.interviews ?? 0,
    trendDir: 'flat', trend: '本周面试场次',
    icon: ICONS.interview, ...SPARK.interview,
  },
  {
    key: 'match', label: '匹配推荐',
    value: stats.value.matchCount ?? stats.value.matches ?? 0,
    trendDir: 'up', trend: '高匹配度候选人',
    icon: ICONS.match, ...SPARK.match,
  },
])

const activities = computed(() => {
  const raw = stats.value.recentActivities || stats.value.activities
  if (Array.isArray(raw) && raw.length) {
    return raw.slice(0, 5).map((a) => ({
      dotClass: a.color || a.dotClass || 'accent',
      text: a.text || a.content || a.title || '',
      time: a.time || a.createdAt || '',
    }))
  }
  return []
})

const upcomingInterviews = computed(() => {
  const raw = stats.value.upcomingInterviews || stats.value.interviewsList
  if (Array.isArray(raw) && raw.length) {
    return raw.slice(0, 5).map((iv) => ({
      avatar: (iv.candidateName || iv.name || '候').charAt(0),
      name: iv.candidateName || iv.name || '候选人',
      detail: [iv.jobTitle || iv.position, iv.scheduledAt || iv.time].filter(Boolean).join(' · '),
      badge: iv.badge || '即将',
      badgeClass: iv.badgeClass || 'soon',
    }))
  }
  return []
})

async function loadStats() {
  loading.value = true
  try {
    const data = await dashboardStats()
    stats.value = data || {}
  } catch {
    // 静默失败，显示默认值
  } finally {
    // brief skeleton for nicer transition
    setTimeout(() => { loading.value = false }, 400)
  }
}

onMounted(loadStats)
</script>
