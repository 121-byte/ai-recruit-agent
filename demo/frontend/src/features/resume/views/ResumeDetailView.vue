<template>
  <MainLayout>
    <!-- 顶栏：返回 + 标题 + 操作 -->
    <div class="detail-topbar">
      <button class="btn btn-secondary btn-sm" @click="goBack">
        <svg width="14" height="14" viewBox="0 0 24 24"><path d="M19 12H5"/><polyline points="12 19 5 12 12 5"/></svg>
        返回列表
      </button>
      <div class="detail-title">
        <span class="avatar">{{ nameInitial }}</span>
        <div>
          <div class="dt-name">{{ resume?.candidateName || resume?.name || '未命名' }}</div>
          <div class="dt-sub">{{ resume?.intendedPosition || sdBasic.intendedPosition || '应聘简历' }}</div>
        </div>
      </div>
      <div class="detail-topbar-actions" v-if="resume">
        <span class="status-pill" :class="`st-${resume.status}`">{{ statusText(resume.status) }}</span>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="!resume && !loading" class="empty">未加载到简历数据</div>

      <div v-else-if="resume" class="detail-body">
        <!-- 概览卡片 -->
        <section class="card">
          <div class="card-head"><h3>基本信息</h3></div>
          <div class="meta-grid">
            <div class="meta-item"><span class="meta-k">姓名</span><span class="meta-v">{{ resume.candidateName || resume.name || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">意向岗位</span><span class="meta-v">{{ sdBasic.intendedPosition || resume.intendedPosition || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">学历</span><span class="meta-v">{{ resume.education || sdBasic.education || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">学校</span><span class="meta-v">{{ resume.school || sdBasic.school || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">专业</span><span class="meta-v">{{ resume.major || sdBasic.major || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">工作年限</span><span class="meta-v">{{ resume.yearsExperience ?? sdBasic.work_years ?? sdBasic.workYears ?? '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">手机</span><span class="meta-v">{{ resume.phone || sdBasic.phone || '—' }}</span></div>
            <div class="meta-item"><span class="meta-k">邮箱</span><span class="meta-v">{{ resume.email || sdBasic.email || '—' }}</span></div>
            <div class="meta-item" v-if="analyzedAt"><span class="meta-k">分析时间</span><span class="meta-v">{{ analyzedAt }}</span></div>
          </div>
        </section>

        <!-- 自校验结论卡片 -->
        <section v-if="validation" class="card">
          <div class="card-head">
            <h3>自校验结论</h3>
            <span class="v-tag" :class="validationClass">{{ validationLabel }}</span>
          </div>
          <div class="validation-text markdown-body" v-html="renderMarkdown(validationText)"></div>
        </section>

        <!-- 四维度分析卡片 -->
        <div class="analysis-grid">
          <section class="card">
            <div class="card-head"><h3>① 结构化数据</h3><span class="card-hint" v-if="!hasStructured">未解析</span></div>
            <EmptyHint v-if="!hasStructured" text="尚未解析，结构化数据为空" />
            <JsonView v-else :data="structuredData" />
          </section>

          <section class="card">
            <div class="card-head"><h3>② 隐性能力</h3><span class="card-hint" v-if="!implicitInsights">未挖掘</span></div>
            <EmptyHint v-if="!implicitInsights" text="尚未进行隐性能力挖掘" />
            <JsonView v-else :data="implicitInsights" />
          </section>

          <section class="card">
            <div class="card-head"><h3>③ 风险评估</h3><span class="card-hint" v-if="!riskAssessment">未评估</span></div>
            <EmptyHint v-if="!riskAssessment" text="尚未进行风险评估" />
            <JsonView v-else :data="riskAssessment" />
          </section>

          <section class="card">
            <div class="card-head"><h3>④ 潜力评估</h3><span class="card-hint" v-if="!potentialAssessment">未评估</span></div>
            <EmptyHint v-if="!potentialAssessment" text="尚未进行潜力评估" />
            <JsonView v-else :data="potentialAssessment" />
          </section>
        </div>

        <!-- 原始文本卡片 -->
        <section class="card">
          <div class="card-head"><h3>原始文本</h3></div>
          <EmptyHint v-if="!resume.rawText" text="无原始文本" />
          <pre v-else class="raw-text">{{ resume.rawText }}</pre>
        </section>
      </div>
    </a-spin>
  </MainLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getResume } from '@/api'
import MainLayout from '@/components/MainLayout.vue'
import JsonView from '@/components/JsonView.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const resume = ref(null)

const EmptyHint = {
  props: ['text'],
  template: '<div class="empty">{{ text }}</div>',
}

const parsed = computed(() => resume.value?.parsedJson || {})
const structuredData = computed(() => parsed.value.structuredData || (parsed.value.basicInfo ? parsed.value : null))
const implicitInsights = computed(() => parsed.value.implicitInsights || null)
const riskAssessment = computed(() => parsed.value.riskAssessment || null)
const potentialAssessment = computed(() => parsed.value.potentialAssessment || null)
const validation = computed(() => parsed.value.validation || null)
const analyzedAt = computed(() => parsed.value.analyzedAt || null)

const sdBasic = computed(() => structuredData.value?.basicInfo || {})
const hasStructured = computed(() => {
  const sd = structuredData.value
  return sd && typeof sd === 'object' && Object.keys(sd).length > 0
})

const nameInitial = computed(() => {
  const n = resume.value?.candidateName || resume.value?.name || '候'
  return String(n).charAt(0)
})

const validationLabel = computed(() => {
  const v = (validation.value || '').trim()
  if (/^(PASS|通过)/i.test(v)) return 'PASS'
  if (/^(WARN|警告)/i.test(v)) return 'WARN'
  if (/^(FAIL|失败)/i.test(v)) return 'FAIL'
  return '校验'
})
const validationClass = computed(() => ({
  PASS: 'v-pass', WARN: 'v-warn', FAIL: 'v-fail',
}[validationLabel.value] || 'v-pass'))
const validationText = computed(() => {
  const v = validation.value || ''
  return v.replace(/^(PASS|WARN|FAIL|通过|警告|失败)[，。:：\s]*/i, '') || v
})

const statusText = (s) => ({ parsed: '已解析', analyzed: '已分析', pending: '待解析', reviewed: '已解析', rejected: '已拒绝' }[s?.toLowerCase()] || s || '未知')

// 自校验结论是 LLM 自然语言(可能含 markdown), 用 marked 渲染并消毒
function renderMarkdown(content) {
  return DOMPurify.sanitize(marked.parse(content || '', { breaks: true, gfm: true }))
}

async function fetchDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    resume.value = await getResume(id)
  } catch (e) {
    message.error(e.message || '加载详情失败')
    resume.value = null
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/resumes')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-topbar { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-4); }
.detail-title { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.avatar { width: 40px; height: 40px; border-radius: 50%; background: var(--accent); color: var(--accent-on); display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.dt-name { font-size: var(--text-lg); font-weight: 700; color: var(--fg); }
.dt-sub { font-size: var(--text-sm); color: var(--muted); }
.detail-topbar-actions { display: flex; align-items: center; gap: 8px; }

.btn-sm { padding: 5px 12px !important; font-size: 12px !important; display: inline-flex; align-items: center; gap: 4px; }

.detail-body { display: flex; flex-direction: column; gap: var(--space-4); }
.card { background: var(--surface); border: 1px solid var(--border-soft); border-radius: var(--radius-md); padding: 18px 20px; box-shadow: var(--elev-ring); }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.card-head h3 { margin: 0; font-size: var(--text-base); font-weight: 700; color: var(--fg); }
.card-hint { font-size: var(--text-xs); color: var(--muted); background: var(--surface-warm); padding: 1px 8px; border-radius: var(--radius-pill); }

.meta-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px 20px; }
.meta-item { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.meta-k { font-size: var(--text-xs); color: var(--muted); }
.meta-v { font-size: var(--text-sm); color: var(--fg); word-break: break-word; }

.analysis-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-4); }
@media (max-width: 960px) { .analysis-grid { grid-template-columns: 1fr; } .meta-grid { grid-template-columns: 1fr 1fr; } }

.status-pill { display: inline-block; padding: 2px 12px; border-radius: var(--radius-pill); font-size: var(--text-xs); background: var(--surface-warm); border: 1px solid var(--border); }
.st-analyzed, .st-reviewed { color: var(--success); border-color: color-mix(in oklab, var(--success), transparent 40%); }
.st-pending { color: var(--muted); }
.st-rejected { color: var(--danger); border-color: color-mix(in oklab, var(--danger), transparent 40%); }

.v-tag { font-size: var(--text-xs); font-weight: 700; padding: 1px 10px; border-radius: var(--radius-pill); }
.v-pass { background: color-mix(in oklab, var(--success), transparent 80%); color: var(--success); }
.v-warn { background: color-mix(in oklab, #c79a3a, transparent 80%); color: #b8862e; }
.v-fail { background: color-mix(in oklab, var(--danger), transparent 80%); color: var(--danger); }
.validation-text { font-size: var(--text-sm); color: var(--fg-2); line-height: 1.6; }
.validation-text :deep(p) { margin: 0 0 8px; }
.validation-text :deep(p:last-child) { margin-bottom: 0; }
.validation-text :deep(ul), .validation-text :deep(ol) { margin: 6px 0; padding-left: 20px; }
.validation-text :deep(li) { margin: 2px 0; }
.validation-text :deep(strong) { color: var(--fg); }
.validation-text :deep(code) { padding: 1px 4px; border-radius: 4px; background: var(--surface-warm); font-size: 0.9em; }
.validation-text :deep(blockquote) { margin: 8px 0; padding-left: 10px; border-left: 3px solid var(--border); color: var(--fg-2); }

.raw-text { background: var(--surface-warm); border: 1px solid var(--border-soft); border-radius: var(--radius-sm); padding: 12px 14px; font-size: var(--text-xs); color: var(--fg-2); white-space: pre-wrap; word-break: break-word; max-height: 60vh; overflow: auto; margin: 0; }
.empty { text-align: center; color: var(--muted); padding: 40px 0; }
</style>
