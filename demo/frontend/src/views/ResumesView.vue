<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">简历管理</h2>
        <a-upload :before-upload="beforeUpload" :show-upload-list="false">
          <a-button type="primary">+ 上传简历</a-button>
        </a-upload>
      </div>

      <a-table
        :columns="columns"
        :data-source="resumes"
        :loading="loading"
        row-key="id"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="analyze(record)">分析</a-button>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusText(record.status) }}
            </a-tag>
          </template>
        </template>
      </a-table>

      <a-modal v-model:open="showProgress" title="上传中" :closable="false" :footer="null">
        <a-progress :percent="uploadPercent" />
      </a-modal>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { listResumes, uploadResume, analyzeResume } from '@/api'
import MainLayout from '@/components/MainLayout.vue'

const resumes = ref([])
const loading = ref(false)
const showProgress = ref(false)
const uploadPercent = ref(0)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '姓名', dataIndex: 'name', key: 'name' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '状态', key: 'status', width: 120 },
  { title: '上传时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

async function load() {
  loading.value = true
  try {
    const data = await listResumes()
    resumes.value = Array.isArray(data) ? data : data?.list || []
  } catch (e) {
    message.error(e.message || '加载简历失败')
  } finally {
    loading.value = false
  }
}

function statusColor(s) {
  return { parsed: 'green', pending: 'orange', failed: 'red' }[s] || 'default'
}
function statusText(s) {
  return { parsed: '已解析', pending: '待解析', failed: '解析失败' }[s] || s || '未知'
}

function beforeUpload(file) {
  showProgress.value = true
  uploadPercent.value = 0
  uploadResume(file, (e) => {
    if (e.total) {
      uploadPercent.value = Math.round((e.loaded / e.total) * 100)
    }
  })
    .then(() => {
      showProgress.value = false
      message.success('上传成功')
      load()
    })
    .catch((e) => {
      showProgress.value = false
      message.error(e.message || '上传失败')
    })
  return false // 阻止默认上传
}

async function analyze(record) {
  const hide = message.loading('正在分析简历...', 0)
  try {
    await analyzeResume(record.id)
    hide()
    message.success('分析完成')
    load()
  } catch (e) {
    hide()
    message.error(e.message || '分析失败')
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
