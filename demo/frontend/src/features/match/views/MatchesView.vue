<template>
  <MainLayout>
    <!-- 岗位选择条 -->
    <div class="match-detail-card" style="margin-bottom:var(--space-5)">
      <div class="match-detail-head">
        <h3>选择岗位进行匹配</h3>
        <button class="btn btn-primary" :disabled="!selectedJobId || matching" @click="runMatch">
          <svg width="14" height="14" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          {{ matching ? '匹配中…' : '重新匹配' }}
        </button>
      </div>
      <div style="padding:var(--space-4) var(--space-5);display:flex;align-items:center;gap:var(--space-3);flex-wrap:wrap">
        <span style="font-size:var(--text-sm);color:var(--fg-2);font-weight:500">目标岗位：</span>
        <a-select
          v-model:value="selectedJobId"
          style="min-width:320px;max-width:100%"
          placeholder="请选择岗位"
          :options="jobOptions"
          :loading="jobLoading"
          @change="onJobChange"
        />
      </div>
    </div>

    <a-spin :spinning="loading">
      <template v-if="matches.length">
        <!-- 统计卡 -->
        <div class="match-summary-grid">
          <div class="match-stat-card">
            <div class="match-stat-icon purple">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
            </div>
            <div class="match-stat-info">
              <div class="stat-num">{{ matches.length }}</div>
              <div class="stat-label">待评估候选人</div>
            </div>
          </div>
          <div class="match-stat-card">
            <div class="match-stat-icon green">
              <svg viewBox="0 0 24 24"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            </div>
            <div class="match-stat-info">
              <div class="stat-num">{{ highMatchCount }}</div>
              <div class="stat-label">高匹配度 (≥85%)</div>
            </div>
          </div>
          <div class="match-stat-card">
            <div class="match-stat-icon amber">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </div>
            <div class="match-stat-info">
              <div class="stat-num">{{ midMatchCount }}</div>
              <div class="stat-label">中等匹配 (60-85%)</div>
            </div>
          </div>
          <div class="match-stat-card">
            <div class="match-stat-icon blue">
              <svg viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
            <div class="match-stat-info">
              <div class="stat-num">{{ avgScore ? avgScore.toFixed(0) : 0 }}</div>
              <div class="stat-label">平均匹配分</div>
            </div>
          </div>
        </div>

        <!-- 排行 -->
        <div class="match-detail-card">
          <div class="match-detail-head">
            <h3>候选人与岗位匹配排行</h3>
            <span class="panel-link">共 {{ matches.length }} 条</span>
          </div>
          <div
            v-for="(m, idx) in sortedMatches"
            :key="idx"
            class="match-row"
            @click="toggleDetail(idx)"
          >
            <span class="match-rank">{{ idx + 1 }}</span>
            <span class="match-name">{{ m.candidateName || m.name || '候选人' }}</span>
            <span class="match-role">{{ m.jobTitle || m.role || '—' }}</span>
            <div class="match-bar-track">
              <div class="match-bar-fill" :style="{ width: pct(m) + '%' }"></div>
            </div>
            <span class="match-pct">{{ pct(m) }}%</span>
            <span class="action-link">{{ openIdx === idx ? '收起' : '详情' }}</span>
          </div>
        </div>

        <!-- 详情 / 评分明细 -->
        <div v-if="openIdx !== null && sortedMatches[openIdx]" class="match-detail-card" style="margin-top:var(--space-5)">
          <div class="match-detail-head">
            <h3>{{ sortedMatches[openIdx].candidateName || sortedMatches[openIdx].name }} · 评分明细</h3>
          </div>
          <div style="padding:var(--space-4) var(--space-5)">
            <div class="score-grid">
              <div v-for="s in scoreItems(sortedMatches[openIdx])" :key="s.label" class="score-item">
                <span class="s-label">{{ s.label }}</span>
                <a-progress :percent="s.pct" size="small" :stroke-color="s.color" />
                <span class="s-value">{{ s.value }}</span>
              </div>
            </div>
            <div v-if="sortedMatches[openIdx].reason" class="match-reason">
              <span class="reason-label">推荐理由：</span>{{ sortedMatches[openIdx].reason }}
            </div>
            <div class="hr-feedback">
              <a-input
                v-model:value="hrFeedback[openIdx]"
                placeholder="HR 反馈..."
                size="small"
                style="max-width:360px"
              />
              <a-button size="small" type="link" @click="saveFeedback(openIdx)">保存反馈</a-button>
            </div>
          </div>
        </div>
      </template>

      <div v-else style="text-align:center;color:var(--muted);padding:64px 0">
        {{ loading ? '加载中…' : '请选择岗位并执行匹配' }}
      </div>
    </a-spin>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listJobs, listResumes, runMatch as runMatchApi, getMatches, matchFeedback } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const route = useRoute()
const router = useRouter()
const selectedJobId = ref(null)
const jobOptions = ref([])
const jobMap = ref({})
const resumeMap = ref({})
const jobLoading = ref(false)
const loading = ref(false)
const matching = ref(false)
const matches = ref([])
const hrFeedback = reactive({})
const openIdx = ref(null)

function toPct(v) {
  const n = Number(v)
  if (isNaN(n)) return 0
  if (n <= 1) return Math.round(n * 100)
  return Math.min(100, Math.max(0, Math.round(n)))
}
function pct(m) {
  return toPct(m.overallScore ?? m.totalScore ?? m.score ?? m.skillScore ?? 0)
}

const sortedMatches = computed(() =>
  [...matches.value].sort((a, b) => pct(b) - pct(a))
)
const highMatchCount = computed(() => matches.value.filter((m) => pct(m) >= 85).length)
const midMatchCount = computed(() => matches.value.filter((m) => { const p = pct(m); return p >= 60 && p < 85; }).length)
const avgScore = computed(() => {
  if (!matches.value.length) return 0
  return matches.value.reduce((s, m) => s + pct(m), 0) / matches.value.length
})

function scoreItems(m) {
  return [
    { label: '技能匹配', value: m.skillScore ?? '-', pct: toPct(m.skillScore), color: '#b46a46' },
    { label: '经验匹配', value: m.experienceScore ?? m.expScore ?? '-', pct: toPct(m.experienceScore ?? m.expScore), color: '#4d8f5a' },
    { label: '软技能', value: m.softScore ?? '-', pct: toPct(m.softScore), color: '#c88735' },
    { label: '向量相似度', value: m.vectorScore ?? '-', pct: toPct(m.vectorScore), color: '#4b5596' },
  ]
}

function toggleDetail(idx) {
  openIdx.value = openIdx.value === idx ? null : idx
}

async function loadJobs() {
  jobLoading.value = true
  try {
    const [jobData, resumeData] = await Promise.all([listJobs(), listResumes()])
    const list = Array.isArray(jobData) ? jobData : jobData?.list || []
    const resumes = Array.isArray(resumeData) ? resumeData : resumeData?.list || []
    jobMap.value = Object.fromEntries(list.map((j) => [String(j.id), j]))
    resumeMap.value = Object.fromEntries(resumes.map((r) => [String(r.id), r]))
    jobOptions.value = list.map((j) => ({ label: j.title || `岗位${j.id}`, value: j.id }))
    // 若从岗位页跳转带 jobId，自动选中并匹配
    if (route.query.jobId) {
      const id = Number(route.query.jobId)
      const opt = jobOptions.value.find((o) => o.value === id || String(o.value) === String(route.query.jobId))
      if (opt) {
        selectedJobId.value = opt.value
        onJobChange(opt.value)
      }
    }
  } catch (e) {
    message.error(e.message || '加载岗位失败')
  } finally {
    jobLoading.value = false
  }
}

async function onJobChange(jobId) {
  loading.value = true
  openIdx.value = null
  try {
    const data = await getMatches(jobId)
    matches.value = normalizeMatches(data)
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
  openIdx.value = null
  try {
    await runMatchApi(selectedJobId.value)
    const data = await getMatches(selectedJobId.value)
    matches.value = normalizeMatches(data)
    message.success('匹配完成')
  } catch (e) {
    message.error(e.message || '匹配失败')
  } finally {
    matching.value = false
  }
}

async function saveFeedback(idx) {
  const match = sortedMatches.value[idx]
  if (!match?.id) return
  try {
    await matchFeedback(match.id, { feedback: hrFeedback[idx] || '' })
    match.hrFeedback = hrFeedback[idx] || ''
    message.success('HR 反馈已保存')
  } catch (e) {
    message.error(e.message || '保存反馈失败')
  }
}

function normalizeMatches(data) {
  const list = Array.isArray(data) ? data : data?.list || data?.matches || data?.candidates || []
  return list.map((match) => {
    const resumeId = match.resumeId ?? match.resume_id
    const jobId = match.jobId ?? match.job_id ?? selectedJobId.value
    const resume = resumeMap.value[String(resumeId)] || {}
    const job = jobMap.value[String(jobId)] || {}
    const details = match.matchDetails || {}
    return {
      ...match,
      resumeId,
      jobId,
      overallScore: match.overallScore ?? match.overall_score,
      skillScore: match.skillScore ?? match.skill_score,
      experienceScore: match.experienceScore ?? match.experience_score,
      softScore: match.softScore ?? match.soft_score,
      vectorScore: match.vectorScore ?? match.vector_score,
      candidateName: match.candidateName || match.name || resume.candidateName || details.candidateName || '候选人',
      jobTitle: match.jobTitle || job.title || details.jobTitle || '—',
      reason: match.reason || match.summary || details.reason || details.summary || '',
    }
  })
}

onMounted(loadJobs)
</script>

<style scoped>
.score-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-3);
}
.score-item { display: flex; flex-direction: column; gap: 2px; }
.s-label { font-size: 12px; color: var(--muted); }
.s-value { font-size: 13px; font-weight: 600; color: var(--fg); }
.match-reason {
  margin-top: var(--space-4);
  font-size: var(--text-sm); color: var(--fg-2);
  background: var(--bg); padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
}
.reason-label { font-weight: 700; color: var(--fg); }
.hr-feedback {
  margin-top: var(--space-3);
  display: flex; align-items: center; gap: var(--space-2);
}
@media (max-width: 768px) {
  .score-grid { grid-template-columns: 1fr 1fr; }
}
</style>
