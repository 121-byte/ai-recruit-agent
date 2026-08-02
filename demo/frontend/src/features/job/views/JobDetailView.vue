<template>
  <MainLayout>
    <div class="detail-topbar">
      <button class="btn btn-secondary btn-sm" @click="goBack">
        <svg width="14" height="14" viewBox="0 0 24 24"><path d="M19 12H5" /><polyline points="12 19 5 12 12 5" /></svg>
        返回列表
      </button>
      <div class="detail-title">
        <span class="title-mark">JD</span>
        <div>
          <div class="dt-name">{{ job?.title || '未命名岗位' }}</div>
          <div class="dt-sub">{{ [job?.department, job?.level, job?.location].filter(Boolean).join(' · ') || '岗位详情' }}</div>
        </div>
      </div>
      <span v-if="job" class="status-pill" :class="`st-${job.status}`">{{ statusText(job.status) }}</span>
    </div>

    <a-spin :spinning="loading">
      <div v-if="!job && !loading" class="empty">未加载到岗位数据</div>

      <div v-else-if="job" class="detail-body">
        <section class="card">
          <div class="card-head"><h3>岗位信息</h3></div>
          <div class="meta-grid">
            <div class="meta-item"><span class="meta-k">部门</span><span class="meta-v">{{ job.department || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">职级</span><span class="meta-v">{{ job.level || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">工作地点</span><span class="meta-v">{{ job.location || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">岗位类别</span><span class="meta-v">{{ job.category || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">薪资范围</span><span class="meta-v">{{ salaryText }}</span></div>
            <div class="meta-item"><span class="meta-k">学历要求</span><span class="meta-v">{{ job.education || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">经验要求</span><span class="meta-v">{{ experienceText }}</span></div>
            <div class="meta-item"><span class="meta-k">招聘人数</span><span class="meta-v">{{ job.headcount ?? '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">最近更新</span><span class="meta-v">{{ formatDate(job.updatedAt || job.createdAt) }}</span></div>
          </div>
        </section>

        <section class="card">
          <div class="card-head"><h3>岗位描述</h3></div>
          <pre v-if="job.jdText" class="jd-text">{{ job.jdText }}</pre>
          <EmptyHint v-else text="暂未填写岗位描述" />
        </section>

        <section class="analysis-section">
          <div class="section-heading">
            <div>
              <h3>AI 岗位分析</h3>
              <p>分析后可查看与简历结构对齐的技能要求、职责、项目方向、学历与硬性门槛。</p>
            </div>
            <span v-if="!hasAnalysis" class="card-hint">尚未分析</span>
          </div>
          <div class="analysis-grid">
            <section class="card">
              <div class="card-head"><h3>技能要求</h3></div>
              <JsonView v-if="hasSkills" :data="parsed.skills" />
              <EmptyHint v-else text="暂无技能要求" />
            </section>
            <section class="card">
              <div class="card-head"><h3>岗位职责</h3></div>
              <JsonView v-if="hasResponsibilities" :data="parsed.responsibilities" />
              <EmptyHint v-else text="暂无职责分析" />
            </section>
            <section class="card">
              <div class="card-head"><h3>项目方向</h3></div>
              <JsonView v-if="hasProjectContext" :data="parsed.projectContext" />
              <EmptyHint v-else text="暂无项目方向" />
            </section>
            <section class="card">
              <div class="card-head"><h3>学历要求</h3></div>
              <JsonView v-if="hasEducation" :data="parsed.education" />
              <EmptyHint v-else text="暂无学历要求" />
            </section>
            <section class="card">
              <div class="card-head"><h3>硬性门槛</h3></div>
              <JsonView v-if="hasRequirements" :data="parsed.requirements" />
              <EmptyHint v-else text="暂无硬性门槛" />
            </section>
            <section class="card">
              <div class="card-head"><h3>角色图谱</h3></div>
              <JsonView v-if="hasRoleGraph" :data="parsed.roleGraph" />
              <EmptyHint v-else text="暂无角色关系" />
            </section>
            <section class="card full-width">
              <div class="card-head"><h3>成长路径</h3></div>
              <JsonView v-if="hasGrowthPath" :data="parsed.growthPath" />
              <EmptyHint v-else text="暂无成长路径" />
            </section>
          </div>
        </section>
      </div>
    </a-spin>
  </MainLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getJob } from '@/api'
import MainLayout from '@/components/MainLayout.vue'
import JsonView from '@/components/JsonView.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const job = ref(null)

const EmptyHint = {
  props: ['text'],
  template: '<div class="empty">{{ text }}</div>',
}

const hasValue = (value) => value && typeof value === 'object' && Object.keys(value).length > 0
const hasArray = (value) => Array.isArray(value) && value.length > 0
const parsed = computed(() => job.value?.parsedJson || {})
const hasSkills = computed(() => hasArray(parsed.value.skills))
const hasResponsibilities = computed(() => hasArray(parsed.value.responsibilities))
const hasProjectContext = computed(() => hasArray(parsed.value.projectContext))
const hasEducation = computed(() => hasValue(parsed.value.education))
const hasRequirements = computed(() => hasValue(parsed.value.requirements))
const hasRoleGraph = computed(() => hasValue(parsed.value.roleGraph))
const hasGrowthPath = computed(() => hasArray(parsed.value.growthPath))
const hasAnalysis = computed(() =>
  hasSkills.value || hasResponsibilities.value || hasProjectContext.value
  || hasEducation.value || hasRequirements.value || hasRoleGraph.value || hasGrowthPath.value)

const salaryText = computed(() => {
  if (job.value?.salaryMin != null && job.value?.salaryMax != null) return `${job.value.salaryMin}K-${job.value.salaryMax}K`
  if (job.value?.salaryMin != null) return `${job.value.salaryMin}K 起`
  if (job.value?.salaryMax != null) return `至 ${job.value.salaryMax}K`
  return '—'
})

const experienceText = computed(() => {
  if (job.value?.experienceMin != null && job.value?.experienceMax != null) return `${job.value.experienceMin}-${job.value.experienceMax} 年`
  if (job.value?.experienceMin != null) return `${job.value.experienceMin} 年及以上`
  if (job.value?.experienceMax != null) return `${job.value.experienceMax} 年以内`
  return '—'
})

function statusText(status) {
  return ({ active: '招聘中', open: '招聘中', draft: '草稿', closed: '已关闭' })[String(status || '').toLowerCase()] || status || '未知'
}

function formatDate(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function fetchDetail() {
  if (!route.params.id) return
  loading.value = true
  try {
    job.value = await getJob(route.params.id)
  } catch (error) {
    job.value = null
    message.error(error.message || '加载岗位详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/jobs')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-topbar { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-4); }
.detail-title { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.title-mark { width: 40px; height: 40px; display: grid; place-items: center; flex-shrink: 0; border-radius: var(--radius-sm); background: var(--surface-warm); border: 1px solid var(--border); color: var(--accent); font-size: var(--text-sm); font-weight: 800; letter-spacing: .04em; }
.dt-name { color: var(--fg); font-size: var(--text-lg); font-weight: 700; }
.dt-sub { color: var(--muted); font-size: var(--text-sm); }
.btn-sm { display: inline-flex; align-items: center; gap: 4px; padding: 5px 12px !important; font-size: 12px !important; }
.status-pill { padding: 2px 12px; border: 1px solid var(--border); border-radius: var(--radius-pill); background: var(--surface-warm); font-size: var(--text-xs); }
.st-active, .st-open { color: var(--success); border-color: color-mix(in oklab, var(--success), transparent 40%); }
.st-closed { color: var(--danger); border-color: color-mix(in oklab, var(--danger), transparent 40%); }
.detail-body { display: flex; flex-direction: column; gap: var(--space-4); }
.card { padding: 18px 20px; border: 1px solid var(--border-soft); border-radius: var(--radius-md); background: var(--surface); box-shadow: var(--elev-ring); }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.card-head h3, .section-heading h3 { margin: 0; color: var(--fg); font-size: var(--text-base); font-weight: 700; }
.meta-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px 20px; }
.meta-item { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.meta-k { color: var(--muted); font-size: var(--text-xs); }
.meta-v { color: var(--fg); font-size: var(--text-sm); word-break: break-word; }
.jd-text { max-height: 52vh; margin: 0; padding: 12px 14px; overflow: auto; border: 1px solid var(--border-soft); border-radius: var(--radius-sm); background: var(--surface-warm); color: var(--fg-2); font-size: var(--text-sm); line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.analysis-section { display: flex; flex-direction: column; gap: 12px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-heading p { margin: 4px 0 0; color: var(--muted); font-size: var(--text-sm); }
.card-hint { padding: 2px 9px; border-radius: var(--radius-pill); background: var(--surface-warm); color: var(--muted); font-size: var(--text-xs); white-space: nowrap; }
.analysis-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.full-width { grid-column: 1 / -1; }
.empty { padding: 40px 0; color: var(--muted); text-align: center; }
@media (max-width: 960px) { .meta-grid { grid-template-columns: repeat(2, 1fr); } .analysis-grid { grid-template-columns: 1fr; } .full-width { grid-column: auto; } }
@media (max-width: 640px) { .detail-topbar { align-items: flex-start; flex-wrap: wrap; } .status-pill { margin-left: 50px; } .meta-grid { grid-template-columns: 1fr; } }
</style>
