<template>
  <div class="hitl-card">
    <div class="hitl-header">
      <span class="hitl-icon">✋</span>
      <span class="hitl-title">人工确认请求</span>
    </div>
    <div class="hitl-body">
      <div class="hitl-desc">{{ data.description || data.message || '请确认以下操作' }}</div>
      <div v-if="data.payload" class="hitl-payload">
        <pre>{{ formatJson(data.payload) }}</pre>
      </div>
    </div>
    <div class="hitl-actions">
      <a-button type="primary" @click="emit('confirm', data)">确认</a-button>
      <a-button danger @click="emit('reject', data)">拒绝</a-button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  data: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['confirm', 'reject'])

function formatJson(v) {
  try {
    return JSON.stringify(v, null, 2)
  } catch (e) {
    return String(v)
  }
}
</script>

<style scoped>
.hitl-card {
  border: 1px solid #ffd591;
  background: #fffbe6;
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
}
.hitl-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #d48806;
  margin-bottom: 8px;
}
.hitl-desc {
  font-size: 13px;
  color: #333;
  margin-bottom: 8px;
}
.hitl-payload pre {
  margin: 0 0 8px;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}
.hitl-actions {
  display: flex;
  gap: 8px;
}
</style>
