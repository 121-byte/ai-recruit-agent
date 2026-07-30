<template>
  <!-- 递归渲染任意 JSON 节点。上下文感知标签 + 评分/等级徽章, 保证各维度展示统一。 -->
  <div class="jv">
    <!-- 对象 -->
    <div v-if="isObject(data)" class="jv-obj">
      <div v-if="!entries.length" class="jv-empty">暂无</div>
      <div v-for="[k, v] in entries" :key="k" class="jv-row">
        <span class="jv-key">{{ label(k) }}</span>
        <span class="jv-colon">：</span>
        <span v-if="isBadgeKey(k, v)" class="jv-badge" :class="badgeClass(k, v)">{{ badgeText(v) }}</span>
        <JsonView v-else :data="v" :ctx="k" />
      </div>
    </div>

    <!-- 数组 -->
    <div v-else-if="isArray(data)" class="jv-arr">
      <span v-if="!data.length" class="jv-empty">暂无</span>
      <div v-else-if="allPrimitive(data)" class="jv-tags">
        <span v-for="(item, i) in data" :key="i" class="jv-tag">{{ String(item) }}</span>
      </div>
      <div v-else class="jv-cards">
        <div v-for="(item, i) in data" :key="i" class="jv-card">
          <JsonView :data="item" :ctx="ctx" />
        </div>
      </div>
    </div>

    <!-- 基础类型 -->
    <span v-else-if="typeof data === 'string'" class="jv-str" :class="{ long: data.length > 80 }">{{ data || '—' }}</span>
    <span v-else-if="typeof data === 'number' || typeof data === 'boolean'" class="jv-num">{{ data }}</span>
    <span v-else class="jv-empty">—</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: [Object, Array, String, Number, Boolean], default: () => undefined },
  // ctx = 本节点所属的字段名, 用于上下文感知标签 (如 skills 下的 name→技能, basicInfo 下的 name→姓名)
  ctx: { type: String, default: '' },
})

const isArray = (v) => Array.isArray(v)
const isObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v)
const allPrimitive = (arr) => arr.every((x) => x === null || typeof x !== 'object')

const entries = computed(() => {
  if (!isObject(props.data)) return []
  return Object.entries(props.data).filter(([, v]) => v !== null && v !== undefined && !(typeof v === 'string' && v.trim() === ''))
})

// 完整字段→中文映射 (覆盖 4 轮提示词涉及的所有字段)
const MAP = {
  basicInfo: '基本信息', skills: '技能', workExperience: '工作经历', work_experience: '工作经历',
  projects: '项目经历', education: '教育经历', certifications: '证书',
  phone: '手机', email: '邮箱', graduationYear: '毕业年份', workYears: '工作年限', work_years: '工作年限',
  intendedPosition: '意向岗位', intendedPositionCategory: '岗位类别',
  company: '公司', title: '职位', period: '时间段', periods: '时间段', duration: '时长',
  responsibilities: '职责', achievements: '成就', role: '角色',
  techStack: '技术栈', tech_stack: '技术栈', description: '描述', highlights: '亮点',
  degree: '学位', startYear: '开始', endYear: '结束', issuer: '颁发机构', year: '年份',
  projectDepth: '项目深度', leadership: '领导力', crossTeamCollaboration: '跨团队协作',
  problemSolving: '问题解决', careerProgression: '职业轨迹', learningAbility: '学习能力',
  communicationSkill: '沟通表达',
  employmentGaps: '就业空窗', exaggerationRisks: '夸大风险', jobHopping: '频繁跳槽',
  skillInflation: '技能膨胀', educationRisks: '教育风险', stabilityRisk: '稳定性',
  overallRiskLevel: '总体风险等级',
  growthPotential: '成长潜力', cultureFit: '文化匹配', careerStage: '职业阶段',
  recommendedRoles: '推荐岗位', developmentSuggestions: '发展建议', overallRating: '综合评级',
  reasoning: '依据', basis: '依据', evidence: '依据', reason: '理由', reasons: '理由',
  summary: '摘要', details: '详情', score: '评分', rating: '评级', level: '熟练度', years: '年限',
}

// 上下文感知标签
function label(k) {
  // name: 视所属容器决定含义
  if (k === 'name') {
    if (props.ctx === 'skills') return '技能'
    if (props.ctx === 'projects') return '项目'
    if (props.ctx === 'certifications') return '证书'
    if (props.ctx === 'basicInfo' || props.ctx === '') return '姓名'
    return '名称'
  }
  // education: basicInfo 内为"学历", 顶层为"教育经历"
  if (k === 'education') {
    return props.ctx === 'basicInfo' ? '学历' : '教育经历'
  }
  if (MAP[k]) return MAP[k]
  return humanize(k)
}

// 未映射键兜底: snake/camel → 可读单词, 已是中文则原样
function humanize(k) {
  if (/[一-龥]/.test(k)) return k
  let s = String(k).replace(/_/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  return s.charAt(0).toUpperCase() + s.slice(1)
}

// 评分/等级徽章: 统一各维度里 score、overallRiskLevel、overallRating 的展示
const BADGE_KEYS = new Set(['overallRiskLevel', 'overallRating'])
const SCORE_KEYS = new Set(['score', '评分', 'rating', 'stabilityRisk'])
function isBadgeKey(k, v) {
  if (BADGE_KEYS.has(k)) return true
  if (SCORE_KEYS.has(k) && (typeof v === 'number' || (typeof v === 'string' && /^\d+$/.test(v)))) return true
  return false
}
function badgeText(v) {
  return String(v)
}
function badgeClass(k, v) {
  if (k === 'overallRiskLevel') {
    const s = String(v).toUpperCase()
    if (s.includes('LOW')) return 'bd-low'
    if (s.includes('MEDIUM') || s.includes('中')) return 'bd-mid'
    if (s.includes('HIGH') || s.includes('高')) return 'bd-high'
    return 'bd-mid'
  }
  if (k === 'overallRating') {
    const s = String(v).toUpperCase()
    if (s === 'S' || s === 'A') return 'bd-high'
    if (s === 'B') return 'bd-mid'
    if (s === 'C') return 'bd-low'
    return 'bd-mid'
  }
  return 'bd-score' // 纯数字评分
}
</script>

<style scoped>
.jv { font-size: var(--text-sm); color: var(--fg-2); line-height: 1.6; }
.jv-row { display: flex; gap: 4px; padding: 2px 0; flex-wrap: wrap; align-items: baseline; }
.jv-key { color: var(--fg); font-weight: 600; white-space: nowrap; }
.jv-colon { color: var(--muted); }
.jv-str { color: var(--fg-2); word-break: break-word; }
.jv-str.long { display: block; white-space: pre-wrap; background: var(--surface-warm); padding: 6px 10px; border-radius: var(--radius-sm); margin-top: 2px; }
.jv-num { color: var(--accent); font-weight: 600; }
.jv-empty { color: var(--muted); font-style: italic; }
.jv-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.jv-tag { background: var(--surface-warm); color: var(--fg); border: 1px solid var(--border-soft); padding: 2px 10px; border-radius: var(--radius-pill); font-size: var(--text-xs); }
.jv-cards { display: flex; flex-direction: column; gap: 10px; }
.jv-card { background: var(--surface-warm); border: 1px solid var(--border-soft); border-radius: var(--radius-sm); padding: 10px 12px; }
.jv-obj { display: flex; flex-direction: column; }
.jv-arr { display: flex; flex-direction: column; gap: 6px; }

.jv-badge { display: inline-block; min-width: 28px; text-align: center; font-size: var(--text-xs); font-weight: 700; padding: 1px 9px; border-radius: var(--radius-pill); }
.bd-score { background: color-mix(in oklab, var(--accent), transparent 82%); color: var(--accent); }
.bd-low { background: color-mix(in oklab, var(--success), transparent 80%); color: var(--success); }
.bd-mid { background: color-mix(in oklab, #c79a3a, transparent 80%); color: #b8862e; }
.bd-high { background: color-mix(in oklab, var(--danger), transparent 80%); color: var(--danger); }
</style>
