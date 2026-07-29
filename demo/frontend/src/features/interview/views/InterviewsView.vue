<template>
  <MainLayout>
    <div class="jobs-toolbar">
      <div class="search-field">
        <svg class="search-icon" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input v-model="keyword" type="text" placeholder="搜索候选人、岗位…" />
      </div>
      <button class="btn btn-primary" @click="showCreate = true">
        <svg width="16" height="16" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        安排面试
      </button>
    </div>

    <div class="filter-row">
      <span
        v-for="f in filters"
        :key="f.key"
        class="chip"
        :class="{ active: activeFilter === f.key }"
        @click="activeFilter = f.key"
      >{{ f.label }}</span>
    </div>

    <div class="job-table-wrap">
      <a-spin :spinning="loading">
        <table class="job-table">
          <thead>
            <tr>
              <th>候选人</th>
              <th>应聘岗位</th>
              <th>面试官</th>
              <th>时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="iv in filteredList" :key="iv.id">
              <td><strong>{{ iv.candidateName || '候选人' }}</strong></td>
              <td>{{ iv.jobTitle || iv.position || '—' }}</td>
              <td>{{ iv.interviewer || iv.interviewerName || '—' }}</td>
              <td>{{ formatDate(iv.scheduledAt) }}</td>
              <td><span class="status-tag" :style="statusStyle(iv.status)">{{ statusText(iv.status) }}</span></td>
              <td>
                <span class="action-link" @click="generateQuestions(iv)">出题</span>
                ·
                <span class="action-link" @click="goAgent(iv)">AI 面试</span>
              </td>
            </tr>
            <tr v-if="!filteredList.length">
              <td colspan="6" style="text-align:center;color:var(--muted);padding:32px">
                {{ loading ? '加载中…' : '暂无面试安排' }}
              </td>
            </tr>
          </tbody>
        </table>
      </a-spin>
    </div>

    <a-modal v-model:open="showQuestions" title="面试题目" width="600px" :footer="null">
      <div v-if="questionsLoading" style="text-align:center;padding:24px">
        <a-spin tip="生成中..." />
      </div>
      <ol v-else style="padding-left:20px;margin:0">
        <li v-for="(q, i) in questions" :key="i" style="margin-bottom:12px;font-size:14px">
          <span style="display:inline;margin-right:8px">{{ questionText(q) }}</span>
          <a-tag v-if="q.type" size="small">{{ q.type }}</a-tag>
        </li>
      </ol>
    </a-modal>

    <a-modal v-model:open="showCreate" title="安排面试" @ok="handleCreate" :confirm-loading="creating">
      <a-form :model="form" layout="vertical">
        <a-form-item label="候选人" required>
          <a-select
            v-model:value="form.resumeId"
            placeholder="请选择候选人"
            show-search
            :filter-option="filterOption"
            :options="resumeOptions"
          />
        </a-form-item>
        <a-form-item label="岗位" required>
          <a-select
            v-model:value="form.jobId"
            placeholder="请选择岗位"
            show-search
            :filter-option="filterOption"
            :options="jobOptions"
          />
        </a-form-item>
        <a-form-item label="面试官">
          <a-input v-model:value="form.interviewer" placeholder="如：张经理" />
        </a-form-item>
        <a-form-item label="面试时间">
          <a-date-picker
            v-model:value="form.scheduledAt"
            show-time
            style="width:100%"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listInterviews, generateInterviewQuestions, createInterview, listJobs, listResumes } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const router = useRouter()
const interviews = ref([])
const loading = ref(false)
const keyword = ref('')
const activeFilter = ref('all')
const showQuestions = ref(false)
const questions = ref([])
const questionsLoading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
const jobOptions = ref([])
const resumeOptions = ref([])
const jobMap = ref({})
const resumeMap = ref({})
const form = reactive({ resumeId: null, jobId: null, interviewer: '', scheduledAt: null })

const filters = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待安排' },
  { key: 'scheduled', label: '已安排' },
  { key: 'in_progress', label: '进行中' },
  { key: 'completed', label: '已完成' },
  { key: 'cancelled', label: '已取消' },
]

const filteredList = computed(() => {
  let list = interviews.value
  if (activeFilter.value !== 'all') {
    list = list.filter((iv) => (iv.status || '').toLowerCase() === activeFilter.value)
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((iv) =>
      (iv.candidateName || '').toLowerCase().includes(kw) ||
      (iv.jobTitle || '').toLowerCase().includes(kw)
    )
  }
  return list
})

function statusText(s) {
  return {
    pending: '待安排', scheduled: '已安排', in_progress: '进行中',
    completed: '已完成', cancelled: '已取消',
  }[s] || s || '未知'
}
function statusStyle(s) {
  const map = {
    scheduled: 'background:rgba(77,143,90,0.12);color:var(--success)',
    pending: 'background:var(--border-soft);color:var(--muted)',
    in_progress: 'background:rgba(200,135,53,0.12);color:var(--warn)',
    completed: 'background:var(--border-soft);color:var(--muted)',
    cancelled: 'background:rgba(184,76,76,0.12);color:var(--danger)',
  }
  return map[s] || 'background:var(--border-soft);color:var(--muted)'
}
function formatDate(d) {
  if (!d) return '—'
  try {
    const dt = new Date(d)
    const pad = (n) => String(n).padStart(2, '0')
    return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`
  } catch {
    return String(d)
  }
}

function questionText(q) {
  if (typeof q === 'string') return q
  return q.content || q.question || q.text || ''
}

async function load() {
  loading.value = true
  try {
    await ensureLookups()
    const data = await listInterviews()
    const list = Array.isArray(data) ? data : data?.list || []
    interviews.value = list.map(normalizeInterview)
  } catch (e) {
    message.error(e.message || '加载面试失败')
  } finally {
    loading.value = false
  }
}

async function ensureLookups() {
  if (jobOptions.value.length && resumeOptions.value.length) return
  const [jobData, resumeData] = await Promise.all([listJobs(), listResumes()])
  const jobs = Array.isArray(jobData) ? jobData : jobData?.list || []
  const resumes = Array.isArray(resumeData) ? resumeData : resumeData?.list || []
  jobMap.value = Object.fromEntries(jobs.map((job) => [String(job.id), job]))
  resumeMap.value = Object.fromEntries(resumes.map((resume) => [String(resume.id), resume]))
  jobOptions.value = jobs.map((job) => ({ value: job.id, label: job.title || `岗位${job.id}` }))
  resumeOptions.value = resumes.map((resume) => ({ value: resume.id, label: resume.candidateName || resume.name || `候选人${resume.id}` }))
}

function normalizeInterview(interview) {
  const resume = resumeMap.value[String(interview.resumeId)] || {}
  const job = jobMap.value[String(interview.jobId)] || {}
  return {
    ...interview,
    candidateName: interview.candidateName || resume.candidateName || '候选人',
    jobTitle: interview.jobTitle || job.title || '—',
    status: (interview.status || 'pending').toLowerCase(),
  }
}

async function generateQuestions(record) {
  showQuestions.value = true
  questions.value = []
  questionsLoading.value = true
  try {
    const data = await generateInterviewQuestions(record.id)
    questions.value = Array.isArray(data) ? data : data?.questions || []
  } catch (e) {
    message.error(e.message || '生成题目失败')
  } finally {
    questionsLoading.value = false
  }
}

async function handleCreate() {
  if (!form.resumeId || !form.jobId) {
    message.warning('请选择候选人和岗位')
    return
  }
  creating.value = true
  try {
    await createInterview({
      resumeId: form.resumeId,
      jobId: form.jobId,
      interviewer: form.interviewer || null,
      scheduledAt: form.scheduledAt,
      status: form.scheduledAt ? 'scheduled' : 'pending',
    })
    message.success('面试已安排')
    showCreate.value = false
    form.resumeId = null
    form.jobId = null
    form.interviewer = ''
    form.scheduledAt = null
    load()
  } catch (e) {
    message.error(e.message || '安排面试失败')
  } finally {
    creating.value = false
  }
}

function filterOption(input, option) {
  return String(option?.label || '').toLowerCase().includes(input.toLowerCase())
}

function goAgent(record) {
  router.push({
    path: '/interview-agent',
    query: record ? { interviewId: record.id, candidateName: record.candidateName } : {},
  })
}

onMounted(load)
</script>
