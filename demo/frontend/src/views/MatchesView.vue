<template>
  <MainLayout>
    <div class="page-container">
      <h2 class="page-title">候选人匹配</h2>

      <a-card class="filter-card">
        <div class="filter-row">
          <span class="filter-label">选择岗位：</span>
          <a-select
            v-model:value="selectedJobId"
            style="width: 320px"
            placeholder="请选择岗位"
            @change="onJobChange"
            :options="jobOptions"
            :loading="jobLoading"
          />
          <a-button type="primary" @click="runMatch" :loading="matching" :disabled="!selectedJobId">
            重新匹配
          </a-button>
        </div>
      </a-card>

      <a-spin :spinning="loading">
        <div v-if="matches.length" class="match-list">
          <a-card
            v-for="(m, idx) in matches"
            :key="idx"
            class="match-item"
          >
            <div class="match-header">
              <span class="rank">#{{ idx + 1 }}</span>
              <span class="candidate-name">{{ m.candidateName || m.name || '候选人' }}</span>
              <span class="total-score">总分：{{ m.totalScore ?? m.score ?? '-' }}</span>
            </div>
            <div class="score-grid">
              <div class="score-item">
                <span class="s-label">技能匹配</span>
                <a-progress :percent="toPct(m.skillScore)" size="small" stroke-color="#1677ff" />
                <span class="s-value">{{ m.skillScore ?? '-' }}</span>
              </div>
              <div class="score-item">
                <span class="s-label">经验匹配</span>
                <a-progress :percent="toPct(m.expScore)" size="small" stroke-color="#52c41a" />
                <span class="s-value">{{ m.expScore ?? '-' }}</span>
              </div>
              <div class="score-item">
                <span class="s-label">软技能</span>
                <a-progress :percent="toPct(m.softScore)" size="small" stroke-color="#fa8c16" />
                <span class="s-value">{{ m.softScore ?? '-' }}</span>
              </div>
              <div class="score-item">
                <span class="s-label">向量相似度</span>
                <a-progress :percent="toPct(m.vectorScore)" size="small" stroke-color="#722ed1" />
                <span class="s-value">{{ m.vectorScore ?? '-' }}</span>
              </div>
            </div>
            <div class="match-reason" v-if="m.reason">
              <span class="reason-label">推荐理由：</span>{{ m.reason }}
            </div>
            <div class="hr-feedback">
              <a-input
                v-model:value="hrFeedback[idx]"
                placeholder="HR 反馈..."
                size="small"
                style="width: 360px"
              />
              <a-button size="small" type="link" @click="saveFeedback(idx)">保存</a-button>
            </div>
          </a-card>
        </div>
        <a-empty v-else description="请选择岗位并执行匹配" style="margin-top: 60px" />
      </a-spin>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { listJobs, matchCandidates, getMatches } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const selectedJobId = ref(null)
const jobOptions = ref([])
const jobLoading = ref(false)
const loading = ref(false)
const matching = ref(false)
const matches = ref([])
const hrFeedback = reactive({})

async function loadJobs() {
  jobLoading.value = true
  try {
    const data = await listJobs()
    const list = Array.isArray(data) ? data : data?.list || []
    jobOptions.value = list.map((j) => ({
      label: j.title || `岗位${j.id}`,
      value: j.id
    }))
  } catch (e) {
    message.error(e.message || '加载岗位失败')
  } finally {
    jobLoading.value = false
  }
}

function toPct(v) {
  const n = Number(v)
  if (isNaN(n)) return 0
  if (n <= 1) return Math.round(n * 100)
  return Math.min(100, Math.round(n))
}

async function onJobChange(jobId) {
  loading.value = true
  try {
    let data = await getMatches(jobId)
    if (!data || (Array.isArray(data) && !data.length)) {
      data = await matchCandidates(jobId)
    }
    matches.value = Array.isArray(data) ? data : data?.list || data?.matches || []
  } catch (e) {
    message.error(e.message || '加载匹配结果失败')
    matches.value = []
  } finally {
    loading.value = false
  }
}

async function runMatch() {
  if (!selectedJobId.value) return
  matching.value = true
  try {
    const data = await matchCandidates(selectedJobId.value)
    matches.value = Array.isArray(data) ? data : data?.list || data?.matches || []
    message.success('匹配完成')
  } catch (e) {
    message.error(e.message || '匹配失败')
  } finally {
    matching.value = false
  }
}

function saveFeedback(idx) {
  message.success('HR 反馈已保存（占位）')
}

onMounted(loadJobs)
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.filter-label {
  font-weight: 500;
}
.match-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.match-item {
  border-radius: 8px;
}
.match-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.rank {
  font-size: 18px;
  font-weight: 700;
  color: #1677ff;
}
.candidate-name {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}
.total-score {
  font-size: 14px;
  color: #52c41a;
  font-weight: 600;
}
.score-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.score-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.s-label {
  font-size: 12px;
  color: #888;
}
.s-value {
  font-size: 13px;
  font-weight: 600;
}
.match-reason {
  margin-top: 12px;
  font-size: 13px;
  color: #555;
  background: #fafafa;
  padding: 8px;
  border-radius: 4px;
}
.reason-label {
  font-weight: 600;
}
.hr-feedback {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
