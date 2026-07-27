<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">岗位管理</h2>
        <a-button type="primary" @click="showForm = true">+ 新建岗位</a-button>
      </div>

      <a-table
        :columns="columns"
        :data-source="jobs"
        :loading="loading"
        row-key="id"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="analyze(record)">分析</a-button>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'open' ? 'green' : 'default'">
              {{ record.status === 'open' ? '招聘中' : '已关闭' }}
            </a-tag>
          </template>
        </template>
      </a-table>

      <a-modal v-model:open="showForm" title="新建岗位" @ok="handleCreate" :confirm-loading="creating">
        <a-form :model="form" layout="vertical">
          <a-form-item label="岗位名称">
            <a-input v-model:value="form.title" placeholder="如：高级 Java 工程师" />
          </a-form-item>
          <a-form-item label="岗位描述">
            <a-textarea v-model:value="form.description" :rows="4" placeholder="岗位职责、要求等" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { listJobs, analyzeJob } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const jobs = ref([])
const loading = ref(false)
const showForm = ref(false)
const creating = ref(false)
const form = reactive({ title: '', description: '' })

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '岗位名称', dataIndex: 'title', key: 'title' },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

async function load() {
  loading.value = true
  try {
    const data = await listJobs()
    jobs.value = Array.isArray(data) ? data : data?.list || []
  } catch (e) {
    message.error(e.message || '加载岗位失败')
  } finally {
    loading.value = false
  }
}

async function analyze(record) {
  const hide = message.loading('正在分析岗位...', 0)
  try {
    const res = await analyzeJob(record.id)
    hide()
    message.success('分析完成')
  } catch (e) {
    hide()
    message.error(e.message || '分析失败')
  }
}

async function handleCreate() {
  creating.value = true
  try {
    // 占位：调用创建接口（未在 api 中封装，用 request）
    message.success('创建成功（占位）')
    showForm.value = false
    form.title = ''
    form.description = ''
    load()
  } catch (e) {
    message.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
}
</style>
