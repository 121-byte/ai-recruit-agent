<template>
  <MainLayout>
    <div class="page-container">
      <h2 class="page-title">面试管理</h2>

      <a-table
        :columns="columns"
        :data-source="interviews"
        :loading="loading"
        row-key="id"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="generateQuestions(record)">出题</a-button>
            <a-button type="link" size="small" @click="goAgent(record)">AI 面试</a-button>
          </template>
        </template>
      </a-table>

      <a-modal v-model:open="showQuestions" title="面试题目" width="600px" :footer="null">
        <div v-if="questionsLoading" style="text-align:center;padding:24px">
          <a-spin tip="生成中..." />
        </div>
        <ol v-else class="question-list">
          <li v-for="(q, i) in questions" :key="i" class="q-item">
            <div class="q-text">{{ q.question || q.text || q }}</div>
            <a-tag v-if="q.type" size="small">{{ q.type }}</a-tag>
          </li>
        </ol>
      </a-modal>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listInterviews, generateInterviewQuestions } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const router = useRouter()
const interviews = ref([])
const loading = ref(false)
const showQuestions = ref(false)
const questions = ref([])
const questionsLoading = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '候选人', dataIndex: 'candidateName', key: 'candidateName' },
  { title: '岗位', dataIndex: 'jobTitle', key: 'jobTitle' },
  { title: '面试时间', dataIndex: 'scheduledAt', key: 'scheduledAt', width: 180 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 160 }
]

function statusColor(s) {
  return { scheduled: 'blue', in_progress: 'processing', completed: 'green', cancelled: 'red' }[s] || 'default'
}
function statusText(s) {
  return { scheduled: '已安排', in_progress: '进行中', completed: '已完成', cancelled: '已取消' }[s] || s || '未知'
}

async function load() {
  loading.value = true
  try {
    const data = await listInterviews()
    interviews.value = Array.isArray(data) ? data : data?.list || []
  } catch (e) {
    message.error(e.message || '加载面试失败')
  } finally {
    loading.value = false
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

function goAgent(record) {
  router.push({ path: '/interview-agent', query: { interviewId: record.id } })
}

onMounted(load)
</script>

<style scoped>
.page-title {
  margin: 0 0 16px;
}
.question-list {
  padding-left: 20px;
  margin: 0;
}
.q-item {
  margin-bottom: 12px;
  font-size: 14px;
}
.q-text {
  display: inline;
  margin-right: 8px;
}
</style>
