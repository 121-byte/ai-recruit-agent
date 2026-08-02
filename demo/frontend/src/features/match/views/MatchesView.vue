<template>
  <MainLayout>
    <!-- 岗位选择条 -->
    <div class="match-detail-card" style="margin-bottom:var(--space-5)">
      <div class="match-detail-head">
        <h3>选择岗位进行匹配</h3>
        <button class="btn btn-primary" :disabled="!selectedJobId || matching || !weightTotalValid" @click="runMatch">
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
      <div class="weight-panel">
        <div class="weight-panel-head">
          <span>评分权重</span>
          <span :class="['weight-total', { invalid: !weightTotalValid }]">合计 {{ weightTotal }}%</span>
        </div>
        <div class="weight-grid">
          <div v-for="item in weightFields" :key="item.key" class="weight-item">
            <label>{{ item.label }}</label>
            <a-input-number
              v-model:value="matchWeights[item.key]"
              :min="0"
              :max="100"
              :step="5"
              size="small"
              addon-after="%"
            />
          </div>
        </div>
        <div class="weight-actions">
          <span v-if="!weightTotalValid">权重合计需等于 100%</span>
          <button type="button" class="weight-reset" @click="resetWeights">恢复默认</button>
        </div>
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
            <div class="weight-snapshot">
              <span class="snapshot-label">本次评分权重</span>
              <span v-for="item in displayWeights(sortedMatches[openIdx])" :key="item.key">
                {{ item.label }} {{ item.value }}%
              </span>
            </div>
            <div v-if="sortedMatches[openIdx].reason" class="match-reason">
              <span class="reason-label">推荐理由：</span>{{ sortedMatches[openIdx].reason }}
            </div>
            <div v-if="explanationSections(sortedMatches[openIdx]).length" class="match-explain">
              <div v-for="section in explanationSections(sortedMatches[openIdx])" :key="section.title" class="explain-section">
                <div class="explain-title">{{ section.title }}</div>
                <ul>
                  <li v-for="(item, itemIdx) in section.items" :key="itemIdx">{{ item }}</li>
                </ul>
              </div>
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
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listJobs, listResumes, runMatch as runMatchApi, getMatches, getMatchTask, matchFeedback } from '@/api'
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
const pollingTimer = ref(null)
const activeTaskId = ref('')
const weightFields = [
  { key: 'skillScore', label: '技能' },
  { key: 'experienceScore', label: '经验' },
  { key: 'projectScore', label: '项目' },
  { key: 'vectorScore', label: '向量' },
  { key: 'rerankScore', label: '重排' },
  { key: 'softScore', label: '软素质' },
]
const defaultWeights = {
  skillScore: 30,
  experienceScore: 25,
  projectScore: 20,
  vectorScore: 10,
  rerankScore: 10,
  softScore: 5,
}
const matchWeights = reactive({ ...defaultWeights })

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
const weightTotal = computed(() =>
  weightFields.reduce((sum, item) => sum + Number(matchWeights[item.key] || 0), 0)
)
const weightTotalValid = computed(() => Math.abs(weightTotal.value - 100) < 0.001)

function scoreItems(m) {
  return [
    { label: '技能匹配', value: m.skillScore ?? '-', pct: toPct(m.skillScore), color: '#b46a46' },
    { label: '经验匹配', value: m.experienceScore ?? m.expScore ?? '-', pct: toPct(m.experienceScore ?? m.expScore), color: '#4d8f5a' },
    { label: '项目质量', value: m.projectScore ?? '-', pct: toPct(m.projectScore), color: '#356f8f' },
    { label: '软技能', value: m.softScore ?? '-', pct: toPct(m.softScore), color: '#c88735' },
    { label: '向量相似度', value: m.vectorScore ?? '-', pct: toPct(m.vectorScore), color: '#4b5596' },
    { label: '重排相关', value: m.rerankScore ?? '-', pct: toPct(m.rerankScore), color: '#6f5aa8' },
  ]
}

function resetWeights() {
  Object.assign(matchWeights, defaultWeights)
}

function weightPayload() {
  return {
    weights: Object.fromEntries(
      weightFields.map((item) => [item.key, Number(matchWeights[item.key] || 0)])
    )
  }
}

function displayWeights(m) {
  const snapshot = normalizeWeightSnapshot(m?.weightConfig || m?.matchDetails?.weightConfig)
  return weightFields.map((item) => ({
    ...item,
    value: snapshot[item.key],
  }))
}

function normalizeWeightSnapshot(value) {
  const source = value && typeof value === 'object' ? value : defaultWeights
  return Object.fromEntries(
    weightFields.map((item) => [item.key, formatWeight(source[item.key] ?? defaultWeights[item.key])])
  )
}

function formatWeight(value) {
  const n = Number(value)
  if (isNaN(n)) return 0
  return Number.isInteger(n) ? n : Number(n.toFixed(2))
}

function explanationSections(m) {
  const details = m.matchDetails || {}
  const sections = []
  const matched = detailsList(details.matchedPoints)
  const gaps = detailsList(details.gaps)
  const risks = detailsList(details.risks)
  const questions = detailsList(details.interviewQuestions)
  if (matched.length) sections.push({ title: '匹配证据', items: matched })
  if (gaps.length) sections.push({ title: '能力缺口', items: gaps })
  if (risks.length) sections.push({ title: '风险提示', items: risks })
  if (questions.length) sections.push({ title: '面试追问', items: questions })
  return sections
}

function detailsList(value) {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    if (typeof item === 'string') return item
    if (!item || typeof item !== 'object') return String(item)
    return [
      item.requirement,
      item.status,
      item.evidence,
      item.reason,
      item.severity,
    ].filter(Boolean).join(' · ')
  }).filter(Boolean)
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
  clearTaskPolling()
  loading.value = true
  openIdx.value = null
  try {
    const data = await getMatches(jobId)
    matches.value = normalizeMatches(data)
    await refreshMatchTask(jobId, { poll: true })
  } catch (e) {
    message.error(e.message || '加载匹配结果失败')
    matches.value = []
  } finally {
    loading.value = false
  }
}

async function runMatch() {
  if (!selectedJobId.value) return
  clearTaskPolling()
  matching.value = true
  openIdx.value = null
  try {
    const task = await runMatchApi(selectedJobId.value, weightPayload())
    await handleTaskState(selectedJobId.value, task, { notify: true, poll: true })
  } catch (e) {
    message.error(e.message || '匹配失败')
    matching.value = false
  }
}

function clearTaskPolling() {
  if (pollingTimer.value) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

function startTaskPolling(jobId) {
  clearTaskPolling()
  pollingTimer.value = window.setInterval(async () => {
    try {
      const task = await getMatchTask(jobId)
      await handleTaskState(jobId, task, { notify: true, poll: false })
    } catch (e) {
      // Keep the current running state; the next poll may recover from a transient request error.
    }
  }, 2500)
}

async function refreshMatchTask(jobId, options = {}) {
  const task = await getMatchTask(jobId)
  await handleTaskState(jobId, task, options)
  return task
}

async function handleTaskState(jobId, task, options = {}) {
  const status = task?.status || 'IDLE'
  const isSelectedJob = String(selectedJobId.value || '') === String(jobId || '')
  if (status === 'RUNNING' || task?.running) {
    activeTaskId.value = task?.task_id || ''
    if (isSelectedJob) matching.value = true
    if (options.poll) startTaskPolling(jobId)
    return
  }

  if (activeTaskId.value && task?.task_id && activeTaskId.value !== task.task_id) {
    return
  }

  if (status === 'SUCCESS') {
    clearTaskPolling()
    activeTaskId.value = ''
    if (isSelectedJob) {
      matching.value = false
      const data = await getMatches(jobId)
      matches.value = normalizeMatches(data)
    }
    if (options.notify) message.success('匹配完成')
    return
  }

  if (status === 'FAILED') {
    clearTaskPolling()
    activeTaskId.value = ''
    if (isSelectedJob) matching.value = false
    if (options.notify) message.error(task?.error || '匹配失败')
    return
  }

  if (isSelectedJob) matching.value = false
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
    const details = match.matchDetails || match.match_details || {}
    const scoreBreakdown = details.scoreBreakdown || {}
    const decision = details.decision || {}
    const weightConfig = normalizeWeightSnapshot(match.weightConfig || match.weight_config || details.weightConfig)
    return {
      ...match,
      resumeId,
      jobId,
      overallScore: match.overallScore ?? match.overall_score,
      skillScore: match.skillScore ?? match.skill_score ?? scoreBreakdown.skillScore,
      experienceScore: match.experienceScore ?? match.experience_score ?? scoreBreakdown.experienceScore,
      projectScore: match.projectScore ?? match.project_score ?? scoreBreakdown.projectScore,
      softScore: match.softScore ?? match.soft_score ?? scoreBreakdown.softScore,
      vectorScore: match.vectorScore ?? match.vector_score ?? scoreBreakdown.vectorScore,
      rerankScore: match.rerankScore ?? match.rerank_score ?? scoreBreakdown.rerankScore,
      decisionTier: match.decisionTier || match.decision_tier || decision.tier || '',
      weightConfig,
      matchDetails: details,
      candidateName: match.candidateName || match.name || resume.candidateName || details.candidateName || '候选人',
      jobTitle: match.jobTitle || job.title || details.jobTitle || '—',
      reason: match.reason || match.summary || details.reason || details.summary || '',
    }
  })
}

onMounted(loadJobs)
onBeforeUnmount(clearTaskPolling)
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
.weight-panel {
  margin: 0 var(--space-5) var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg);
}
.weight-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
  font-size: 13px;
  font-weight: 700;
  color: var(--fg);
}
.weight-total {
  color: var(--fg-2);
  font-weight: 600;
}
.weight-total.invalid { color: #b45309; }
.weight-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(92px, 1fr));
  gap: var(--space-2);
}
.weight-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.weight-item label {
  font-size: 12px;
  color: var(--muted);
}
.weight-actions {
  min-height: 24px;
  margin-top: var(--space-2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  color: #b45309;
  font-size: 12px;
}
.weight-reset {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--primary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}
.weight-snapshot {
  margin-top: var(--space-3);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--fg-2);
  font-size: 12px;
}
.weight-snapshot span {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg);
  border: 1px solid var(--border);
}
.weight-snapshot .snapshot-label {
  border: 0;
  padding-left: 0;
  background: transparent;
  color: var(--fg);
  font-weight: 700;
}
.match-reason {
  margin-top: var(--space-4);
  font-size: var(--text-sm); color: var(--fg-2);
  background: var(--bg); padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
}
.reason-label { font-weight: 700; color: var(--fg); }
.match-explain {
  margin-top: var(--space-4);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}
.explain-section {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  background: var(--bg);
}
.explain-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--fg);
}
.explain-section ul {
  margin: 0;
  padding-left: 18px;
  color: var(--fg-2);
  font-size: 12px;
  line-height: 1.6;
}
.hr-feedback {
  margin-top: var(--space-3);
  display: flex; align-items: center; gap: var(--space-2);
}
@media (max-width: 768px) {
  .score-grid { grid-template-columns: 1fr 1fr; }
  .weight-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .match-explain { grid-template-columns: 1fr; }
}
</style>
