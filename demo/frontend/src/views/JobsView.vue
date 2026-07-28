<template>
  <MainLayout>
    <!-- 筛选 + 搜索行 -->
    <div class="jobs-toolbar">
      <div class="filter-group">
        <select v-model="filters.status" class="filter-select" @change="load">
          <option value="">全部状态</option>
          <option value="active">招聘中</option>
          <option value="draft">草稿</option>
          <option value="closed">已关闭</option>
        </select>
        <select v-model="filters.department" class="filter-select" @change="load">
          <option value="">全部部门</option>
          <option v-for="d in deptOptions" :key="d" :value="d">{{ d }}</option>
        </select>
        <select v-model="filters.level" class="filter-select" @change="load">
          <option value="">全部职级</option>
          <option v-for="l in levelOptions" :key="l" :value="l">{{ l }}</option>
        </select>
      </div>
      <div class="jobs-actions">
        <div class="search-field">
          <svg class="search-icon" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="keyword" type="text" placeholder="搜索岗位名称、部门、地点…" @input="onSearch" />
        </div>
        <button class="btn btn-primary" @click="openCreate">
          <svg width="16" height="16" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建岗位
        </button>
      </div>
    </div>

    <!-- 岗位列表 -->
    <div class="job-table-wrap">
      <a-spin :spinning="loading">
        <table class="job-table">
          <thead>
            <tr>
              <th>岗位名称</th>
              <th>部门</th>
              <th>职级</th>
              <th>地点</th>
              <th>薪资范围</th>
              <th>学历</th>
              <th>经验</th>
              <th>HC</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="job in jobs" :key="job.id">
              <td><strong>{{ job.title || '—' }}</strong></td>
              <td>{{ job.department || '—' }}</td>
              <td>{{ job.level || '—' }}</td>
              <td>{{ job.location || '—' }}</td>
              <td>{{ salaryText(job) }}</td>
              <td>{{ job.education || '—' }}</td>
              <td>{{ expText(job) }}</td>
              <td>{{ job.headcount ?? '—' }}</td>
              <td>
                <span class="status-tag" :class="statusClass(job.status)">
                  {{ statusText(job.status) }}
                </span>
              </td>
              <td>{{ formatDate(job.createdAt) }}</td>
              <td>
                <span class="action-link" @click="openEdit(job)">编辑</span>
                <span class="action-sep">·</span>
                <span class="action-link danger" @click="handleDelete(job)">删除</span>
                <template v-if="isHR">
                  <span class="action-sep">·</span>
                  <span class="action-link" @click="analyze(job)">分析</span>
                  <span class="action-sep">·</span>
                  <span class="action-link" @click="goMatches(job)">匹配</span>
                </template>
              </td>
            </tr>
            <tr v-if="!jobs.length">
              <td colspan="11" style="text-align:center;color:var(--muted);padding:32px">
                {{ loading ? '加载中…' : '暂无岗位数据' }}
              </td>
            </tr>
          </tbody>
        </table>
      </a-spin>
    </div>

    <!-- 新建/编辑弹窗 -->
    <a-modal
      v-model:open="showForm"
      :title="editing ? '编辑岗位' : '新建岗位'"
      @ok="handleSave"
      :confirm-loading="saving"
      ok-text="保存"
      width="680px"
    >
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="岗位名称" required>
              <a-input v-model:value="form.title" placeholder="如：高级 Java 工程师" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="部门">
              <a-input v-model:value="form.department" placeholder="如：研发中心" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="职级">
              <a-select v-model:value="form.level" placeholder="选择职级" allow-clear>
                <a-select-option value="实习生">实习生</a-select-option>
                <a-select-option value="初级">初级</a-select-option>
                <a-select-option value="中级">中级</a-select-option>
                <a-select-option value="高级">高级</a-select-option>
                <a-select-option value="资深">资深</a-select-option>
                <a-select-option value="专家">专家</a-select-option>
                <a-select-option value="架构师">架构师</a-select-option>
                <a-select-option value="总监">总监</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="工作地点">
              <a-input v-model:value="form.location" placeholder="如：上海" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="岗位类别">
              <a-input v-model:value="form.category" placeholder="如：技术" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="最低薪资 (K)">
              <a-input-number v-model:value="form.salaryMin" :min="0" style="width:100%" placeholder="15" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="最高薪资 (K)">
              <a-input-number v-model:value="form.salaryMax" :min="0" style="width:100%" placeholder="30" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="招聘人数">
              <a-input-number v-model:value="form.headcount" :min="1" style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="学历要求">
              <a-select v-model:value="form.education" placeholder="不限" allow-clear>
                <a-select-option value="大专">大专</a-select-option>
                <a-select-option value="本科">本科</a-select-option>
                <a-select-option value="硕士">硕士</a-select-option>
                <a-select-option value="博士">博士</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="最低经验 (年)">
              <a-input-number v-model:value="form.experienceMin" :min="0" style="width:100%" placeholder="3" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="最高经验 (年)">
              <a-input-number v-model:value="form.experienceMax" :min="0" style="width:100%" placeholder="5" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="状态">
              <a-select v-model:value="form.status">
                <a-select-option value="draft">草稿</a-select-option>
                <a-select-option value="active">招聘中</a-select-option>
                <a-select-option value="closed">已关闭</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="岗位描述">
          <a-textarea v-model:value="form.jdText" :rows="4" placeholder="岗位职责、任职要求等" />
        </a-form-item>
      </a-form>
    </a-modal>
  </MainLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { listJobs, createJob, updateJob, deleteJob, analyzeJob, listDepartments, listLevels } from '@/api'
import { useAuthStore } from '@/store/auth'
import MainLayout from '@/components/MainLayout.vue'

const router = useRouter()
const authStore = useAuthStore()

const isHR = computed(() => authStore.role === 'HR')

const jobs = ref([])
const loading = ref(false)
const keyword = ref('')
const filters = reactive({ status: '', department: '', level: '' })
const deptOptions = ref([])
const levelOptions = ref([])

const showForm = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({
  title: '', department: '', level: '', location: '', category: '',
  salaryMin: null, salaryMax: null,
  education: '', experienceMin: null, experienceMax: null,
  headcount: 1, status: 'draft', jdText: '',
})

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
    if (filters.department) params.department = filters.department
    if (filters.level) params.level = filters.level
    const data = await listJobs(params)
    jobs.value = Array.isArray(data) ? data : data?.list || []
  } catch (e) {
    message.error(e.message || '加载岗位失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [depts, levels] = await Promise.all([listDepartments(), listLevels()])
    deptOptions.value = Array.isArray(depts) ? depts : []
    levelOptions.value = Array.isArray(levels) ? levels : []
  } catch {
    // 静默
  }
}

// ─── 格式化 ───
function salaryText(job) {
  if (job.salaryMin != null && job.salaryMax != null) return `${job.salaryMin}K-${job.salaryMax}K`
  if (job.salaryMin != null) return `${job.salaryMin}K 起`
  if (job.salaryMax != null) return `至 ${job.salaryMax}K`
  return '—'
}
function expText(job) {
  if (job.experienceMin != null && job.experienceMax != null) return `${job.experienceMin}-${job.experienceMax}年`
  if (job.experienceMin != null) return `${job.experienceMin}年+`
  if (job.experienceMax != null) return `${job.experienceMax}年以内`
  return '—'
}
function statusClass(status) {
  const s = (status || '').toLowerCase()
  if (s === 'active' || s === 'open') return 'open'
  if (s === 'draft') return 'draft'
  return 'closed'
}
function statusText(status) {
  const s = (status || '').toLowerCase()
  if (s === 'active' || s === 'open') return '招聘中'
  if (s === 'draft') return '草稿'
  if (s === 'closed') return '已关闭'
  return status || '未知'
}
function formatDate(d) {
  if (!d) return '—'
  try {
    return new Date(d).toISOString().slice(0, 10)
  } catch {
    return String(d).slice(0, 10)
  }
}

// ─── 新建 / 编辑 ───
function resetForm() {
  form.title = ''; form.department = ''; form.level = ''
  form.location = ''; form.category = ''
  form.salaryMin = null; form.salaryMax = null
  form.education = ''; form.experienceMin = null; form.experienceMax = null
  form.headcount = 1; form.status = 'draft'; form.jdText = ''
}

function openCreate() {
  editing.value = null
  resetForm()
  form.status = 'active'
  showForm.value = true
}

function openEdit(job) {
  editing.value = job.id
  form.title = job.title || ''
  form.department = job.department || ''
  form.level = job.level || ''
  form.location = job.location || ''
  form.category = job.category || ''
  form.salaryMin = job.salaryMin ?? null
  form.salaryMax = job.salaryMax ?? null
  form.education = job.education || ''
  form.experienceMin = job.experienceMin ?? null
  form.experienceMax = job.experienceMax ?? null
  form.headcount = job.headcount ?? 1
  form.status = job.status || 'draft'
  form.jdText = job.jdText || ''
  showForm.value = true
}

async function handleSave() {
  if (!form.title.trim()) {
    message.warning('请输入岗位名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      department: form.department || null,
      level: form.level || null,
      location: form.location || null,
      category: form.category || null,
      salaryMin: form.salaryMin ?? null,
      salaryMax: form.salaryMax ?? null,
      education: form.education || null,
      experienceMin: form.experienceMin ?? null,
      experienceMax: form.experienceMax ?? null,
      headcount: form.headcount || 1,
      status: form.status || 'active',
      jdText: form.jdText || '',
    }
    if (editing.value) {
      await updateJob(editing.value, payload)
      message.success('更新成功')
    } else {
      await createJob(payload)
      message.success('创建成功')
    }
    showForm.value = false
    load()
    loadOptions()
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ─── 删除 ───
function handleDelete(job) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除岗位「${job.title}」吗？此操作不可撤销。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteJob(job.id)
        message.success('删除成功')
        load()
        loadOptions()
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

// ─── 分析 / 匹配（仅HR）───
async function analyze(job) {
  const hide = message.loading('正在分析岗位...', 0)
  try {
    await analyzeJob(job.id)
    hide()
    message.success('分析完成')
  } catch (e) {
    hide()
    message.error(e.message || '分析失败')
  }
}

function goMatches(job) {
  router.push({ path: '/matches', query: { jobId: job.id } })
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
  min-width: 110px;
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

/* ─── 表格 ─── */
.job-table th, .job-table td {
  white-space: nowrap;
}
.job-table td:first-child {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── 状态标签 ─── */
.status-tag.draft {
  background: var(--border-soft);
  color: var(--muted);
}
.status-tag.closed {
  background: rgba(184, 76, 76, 0.10);
  color: var(--danger);
}

/* ─── 操作链接 ─── */
.action-sep {
  color: var(--border);
  margin: 0 2px;
  user-select: none;
}
.action-link.danger {
  color: var(--danger);
}
.action-link.danger:hover {
  opacity: 0.7;
}
</style>
