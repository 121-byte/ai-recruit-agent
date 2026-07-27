<template>
  <MainLayout>
    <div class="page-container">
      <h2 class="page-title">仪表盘</h2>
      <a-spin :spinning="loading">
        <div class="card-grid">
          <a-card class="stat-card" v-for="card in statCards" :key="card.label">
            <a-statistic :title="card.label" :value="card.value" :value-style="{ color: card.color }">
              <template #suffix><span class="stat-suffix">{{ card.suffix }}</span></template>
            </a-statistic>
          </a-card>
        </div>
        <a-card title="最近活动" class="activity-card">
          <a-timeline>
            <a-timeline-item v-for="(act, i) in activities" :key="i" :color="act.color || 'blue'">
              <p class="act-text">{{ act.text }}</p>
              <p class="act-time">{{ act.time }}</p>
            </a-timeline-item>
            <a-timeline-item v-if="!activities.length">
              <span style="color: #bbb">暂无最近活动</span>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-spin>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { dashboardStats } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const loading = ref(false)
const stats = ref({})

const statCards = computed(() => [
  { label: '简历总数', value: stats.value.resumeCount ?? stats.value.resumes ?? 0, color: '#1677ff', suffix: '份' },
  { label: '岗位总数', value: stats.value.jobCount ?? stats.value.jobs ?? 0, color: '#52c41a', suffix: '个' },
  { label: '面试总数', value: stats.value.interviewCount ?? stats.value.interviews ?? 0, color: '#fa8c16', suffix: '场' },
  { label: '匹配总数', value: stats.value.matchCount ?? stats.value.matches ?? 0, color: '#722ed1', suffix: '次' }
])

const activities = computed(() => stats.value.recentActivities || stats.value.activities || [])

async function loadStats() {
  loading.value = true
  try {
    stats.value = (await dashboardStats()) || {}
  } catch (e) {
    message.error(e.message || '加载统计失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
}
.stat-card {
  border-radius: 8px;
}
.stat-suffix {
  font-size: 14px;
  color: #999;
}
.activity-card {
  margin-top: 16px;
}
.act-text {
  margin: 0;
  font-size: 14px;
}
.act-time {
  margin: 0;
  font-size: 12px;
  color: #999;
}
</style>
