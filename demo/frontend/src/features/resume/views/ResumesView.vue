<template>
  <MainLayout>
    <!-- 筛选 + 搜索 + 导入行 -->
    <div class="jobs-toolbar">
      <div class="filter-group">
        <select v-model="filters.status" class="filter-select" @change="load">
          <option value="">全部状态</option>
          <option value="pending">待解析</option>
          <option value="reviewed">已解析</option>
          <option value="rejected">已拒绝</option>
        </select>
        <select v-model="filters.intendedPosition" class="filter-select" @change="load">
          <option value="">全部岗位类别</option>
          <option v-for="p in positionOptions" :key="p" :value="p">{{ p }}</option>
        </select>
        <select v-model="filters.education" class="filter-select" @change="load">
          <option value="">全部学历</option>
          <option v-for="e in eduOptions" :key="e" :value="e">{{ e }}</option>
        </select>
      </div>
      <div class="jobs-actions">
        <div class="search-field" style="min-width:240px">
          <svg class="search-icon" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="keyword" type="text" placeholder="搜索姓名、技能、岗位…" @input="onSearch" />
        </div>
        <a-upload :before-upload="beforeUpload" :show-upload-list="false">
          <button class="btn btn-primary" type="button">
            <svg width="16" height="16" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            导入简历
          </button>
        </a-upload>
      </div>
    </div>

    <!-- 简历卡片列表 -->
    <a-spin :spinning="loading">
      <div v-if="resumes.length" class="resume-grid">
        <div v-for="r in resumes" :key="r.id" class="resume-card">
          <div class="resume-card-header">
            <div class="resume-card-avatar">{{ nameInitial(r) }}</div>
            <div class="resume-card-info">
              <div class="name">{{ r.name || '未命名' }}</div>
              <div class="title">{{ r.intendedPosition || r.title || '应聘简历' }}</div>
            </div>
            <div class="resume-card-menu" @click.stop>
              <svg viewBox="0 0 24 24" width="18" height="18"><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></svg>
            </div>
          </div>

          <!-- 标签行 -->
          <div class="resume-tags">
            <span class="resume-tag" :class="statusTagClass(r.status)">{{ statusText(r.status) }}</span>
            <span v-if="r.education" class="resume-tag edu">{{ r.education }}</span>
            <span v-if="r.workYears" class="resume-tag exp">{{ r.workYears }}年经验</span>
            <span v-if="r.email" class="resume-tag skill">{{ r.email }}</span>
            <span v-if="r.phone" class="resume-tag skill">{{ r.phone }}</span>
          </div>

          <!-- 技能 -->
          <div v-if="r.skills.length" class="resume-tags">
            <span v-for="(skill, i) in r.skills.slice(0, 5)" :key="i" class="resume-tag skill">{{ skill }}</span>
            <span v-if="r.skills.length > 5" class="resume-tag skill">+{{ r.skills.length - 5 }}</span>
          </div>

          <!-- 匹配度 -->
          <div v-if="r.matchScore != null" class="match-bar">
            <span class="match-label">匹配度</span>
            <div class="match-bar-track">
              <div class="match-bar-fill" :style="{ width: toPct(r.matchScore) + '%' }"></div>
            </div>
            <span class="match-pct">{{ toPct(r.matchScore) }}%</span>
          </div>

          <!-- 操作按钮 -->
          <div class="resume-card-actions">
            <button class="btn btn-primary btn-sm" @click="openDetail(r)">详情</button>            <button class="btn btn-secondary btn-sm" @click="openEdit(r)">编辑</button>
            <button class="btn btn-secondary btn-sm danger" @click="handleDelete(r)">删除</button>
            <template v-if="isHR">
              <button class="btn btn-secondary btn-sm" @click="analyzeResume(r)">分析</button>
              <button class="btn btn-primary btn-sm" @click="inviteInterview(r)">邀请面试</button>
            </template>
          </div>
        </div>
      </div>
      <div v-else style="text-align:center;color:var(--muted);padding:48px 0">
        {{ loading ? '加载中…' : '暂无简历数据，点击右上角导入' }}
      </div>
    </a-spin>

    <!-- 上传进度 -->
    <a-modal v-model:open="showProgress" title="上传中" :closable="false" :footer="null">
      <a-progress :percent="uploadPercent" />
    </a-modal>

    <!-- 编辑弹窗 -->
    <a-modal v-model:open="showForm" title="编辑简历" @ok="handleSave" :confirm-loading="saving" ok-text="保存">      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="姓名">
              <a-input v-model:value="form.candidateName" placeholder="候选人姓名" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select v-model:value="form.status">
                <a-select-option value="pending">待解析</a-select-option>
                <a-select-option value="reviewed">已解析</a-select-option>
                <a-select-option value="rejected">已拒绝</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="邮箱">
              <a-input v-model:value="form.email" placeholder="Email" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="电话">
              <a-input v-model:value="form.phone" placeholder="手机号" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注（原始文本）">
          <a-textarea v-model:value="form.rawText" :rows="4" placeholder="简历内容" />
        </a-form-item>
      </a-form>
    </a-modal>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { listResumes, uploadResume, updateResume, deleteResume, analyzeResume as apiAnalyze, getTaskStatus, listPositionCategories, listEducations } from '@/api'
import { useAuthStore } from '@/store/auth'
import MainLayout from '@/components/MainLayout.vue'

const router = useRouter()
const authStore = useAuthStore()
const isHR = computed(() => authStore.role === 'HR')

const resumes = ref([])
const loading = ref(false)
const keyword = ref('')
const filters = reactive({ status: '', intendedPosition: '', education: '' })
const positionOptions = ref([])
const eduOptions = ref([])

const showProgress = ref(false)
const uploadPercent = ref(0)
const showForm = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({ candidateName: '', status: 'pending', email: '', phone: '', rawText: '' })

// ─── 简历详情 ───
function openDetail(r) {
  router.push(`/resumes/${r.id}`)
}

// ─── 搜索防抖 ───
let searchTimer = null
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 300)
}

// ─── 加载数据 ───
async function load() {
  loading.value = true
  try {
    const params = {}
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (filters.status) params.status = filters.status
    if (filters.intendedPosition) params.intendedPosition = filters.intendedPosition
    if (filters.education) params.education = filters.education
    const data = await listResumes(params)
    const list = Array.isArray(data) ? data : data?.list || []
    resumes.value = list.map(normalizeResume)
  } catch (e) {
    message.error(e.message || '加载简历失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [positions, edus] = await Promise.all([listPositionCategories(), listEducations()])
    positionOptions.value = Array.isArray(positions) ? positions : []
    eduOptions.value = Array.isArray(edus) ? edus : []
  } catch { /* 静默 */ }
}

function normalizeResume(resume) {
  const parsed = resume.parsedJson || {}
  // 分析结果为嵌套结构: parsedJson.structuredData.{skills,basicInfo,...}
  // 编辑/导入可能是扁平结构, 两者都兼容
  const sd = parsed.structuredData || {}
  const basic = sd.basicInfo || {}
  const skills = normalizeArray(sd.skills || parsed.skills || parsed.skill_tags || parsed.coreSkills)
  return {
    ...resume,
    name: resume.candidateName || resume.name || basic.name || parsed.name || parsed.candidate_name || '未命名',
    email: resume.email || basic.email || parsed.email || '',
    phone: resume.phone || basic.phone || parsed.phone || parsed.mobile || '',
    title: resume.title || basic.intendedPosition || basic.intended_position || parsed.intended_position || parsed.position || '应聘简历',
    intendedPosition: basic.intendedPosition || basic.intended_position || parsed.intended_position || parsed.target_position || resume.intendedPosition || '',
    education: resume.education || basic.education || parsed.education || parsed.highest_education || '',
    workYears: resume.yearsExperience ?? basic.work_years ?? basic.workYears ?? parsed.work_years ?? parsed.workYears ?? null,
    skills,
    matchScore: resume.matchScore ?? resume.score ?? null,
    status: (resume.status || 'pending').toLowerCase(),
  }
}

function normalizeArray(value) {
  if (Array.isArray(value)) {
    return value.map((el) => {
      if (el == null) return null
      if (typeof el === 'object') {
        // 技能/经历等可能是对象 {name, level, years}, 取名称展示
        return el.name || el.title || el.skill || null
      }
      const s = String(el).trim()
      return s || null
    }).filter(Boolean)
  }
  if (typeof value === 'string') {
    return value.split(/[,，、\s]+/).map(s => s.trim()).filter(Boolean)
  }
  return []
}

function nameInitial(r) {
  return (r.name || '候').charAt(0)
}

// ─── 格式化 ───
function statusText(s) {
  return { reviewed: '已解析', pending: '待解析', rejected: '已拒绝' }[s] || s || '未知'
}
function statusTagClass(s) {
  if (s === 'reviewed') return 'exp'
  if (s === 'pending') return 'skill'
  if (s === 'rejected') return 'edu'
  return 'skill'
}
function toPct(v) {
  const n = Number(v)
  if (isNaN(n)) return 0
  if (n <= 1) return Math.round(n * 100)
  return Math.min(100, Math.round(n))
}

// ─── 上传 ───
function beforeUpload(file) {
  showProgress.value = true
  uploadPercent.value = 0
  uploadResume(file, (e) => {
    if (e.total) uploadPercent.value = Math.round((e.loaded / e.total) * 100)
  }).then(() => {
    showProgress.value = false
    message.success('上传成功')
    load()
    loadOptions()
  }).catch((e) => {
    showProgress.value = false
    message.error(e.message || '上传失败')
  })
  return false
}

// ─── 编辑 ───
function openEdit(r) {
  editing.value = r.id
  form.candidateName = r.candidateName || r.name || ''
  form.status = r.status || 'pending'
  form.email = r.email || ''
  form.phone = r.phone || ''
  form.rawText = r.rawText || ''
  showForm.value = true
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      candidateName: form.candidateName.trim() || null,
      status: form.status || 'pending',
    }
    // 如果有 email/phone/rawText，更新到 parsedJson
    const parsed = {}
    if (form.email) parsed.email = form.email
    if (form.phone) parsed.phone = form.phone
    if (Object.keys(parsed).length) payload.parsedJson = parsed
    if (form.rawText) payload.rawText = form.rawText

    await updateResume(editing.value, payload)
    message.success('更新成功')
    showForm.value = false
    load()
  } catch (e) {
    message.error(e.message || '更新失败')
  } finally {
    saving.value = false
  }
}

// ─── 删除 ───
function handleDelete(r) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除「${r.name}」的简历吗？此操作不可撤销。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteResume(r.id)
        message.success('删除成功')
        load()
        loadOptions()
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

// ─── 分析（仅HR）───
async function analyzeResume(r) {
  const hide = message.loading('正在分析简历...', 0)
  try {
    const res = await apiAnalyze(r.id)
    // mock 同步返回结果对象 (无 taskId); real 异步返回 {taskId}
    if (res && res.taskId) {
      await pollTask(res.taskId, hide)
    } else {
      hide()
      message.success('分析完成')
    }
    load()
  } catch (e) {
    hide()
    message.error(e.message || '分析失败')
  }
}

// 轮询异步任务状态 (real 模式 4+1 轮解析耗时较长)
async function pollTask(taskId, hide) {
  const maxAttempts = 90
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise((res) => setTimeout(res, 2000))
    try {
      const st = await getTaskStatus(taskId)
      if (st && st.status === 'SUCCESS') {
        hide()
        message.success(st.message || '分析完成')
        return
      }
      if (st && st.status === 'FAILED') {
        hide()
        throw new Error(st.message || '任务失败')
      }
      // PENDING/RUNNING 继续轮询
    } catch (e) {
      // 轮询网络错误继续重试
    }
  }
  hide()
  throw new Error('分析任务超时')
}

// ─── 邀请面试（仅HR）───
function inviteInterview(r) {
  router.push({ path: '/interview-agent', query: { candidateName: r.name, resumeId: r.id } })
}

onMounted(() => {
  load()
  loadOptions()
})
</script>

<style scoped>
/* ─── 筛选 + 搜索行 ─── */
.jobs-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
  gap: var(--space-4);
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.filter-select {
  padding: 7px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--fg);
  outline: none;
  cursor: pointer;
  min-width: 120px;
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}
.filter-select:focus {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}
.jobs-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* ─── 简历卡片 ─── */
.resume-card { position: relative; }
.resume-card-menu {
  position: absolute; top: var(--space-4); right: var(--space-4);
  color: var(--muted); cursor: pointer; padding: 4px;
  border-radius: var(--radius-sm);
  transition: background var(--motion-fast), color var(--motion-fast);
}
.resume-card-menu:hover { background: var(--surface-warm); color: var(--fg); }
.resume-card-menu svg { fill: currentColor; stroke: none; }

/* ─── 操作按钮 ─── */
.resume-card-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border-soft);
  flex-wrap: wrap;
}
.btn-sm {
  padding: 5px 12px !important;
  font-size: 12px !important;
}
.btn-sm.danger { color: var(--danger) !important; }
.btn-sm.danger:hover { background: rgba(184,76,76,0.08) !important; }

.match-label {
  font-size: var(--text-xs);
  color: var(--muted);
  white-space: nowrap;
}
</style>
