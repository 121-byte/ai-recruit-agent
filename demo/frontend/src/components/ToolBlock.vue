<template>
  <div class="tool-block">
    <div class="tool-header" @click="toggle">
      <span class="tool-icon">🔧</span>
      <span class="tool-name">{{ name }}</span>
      <span class="tool-status" :class="statusClass">{{ statusText }}</span>
      <span class="toggle-icon">{{ expanded ? '▾' : '▸' }}</span>
    </div>
    <div v-if="expanded" class="tool-body">
      <div v-if="args" class="tool-section">
        <div class="section-label">参数</div>
        <pre>{{ formatJson(args) }}</pre>
      </div>
      <div v-if="result !== null && result !== undefined" class="tool-section">
        <div class="section-label">结果</div>
        <pre>{{ formatJson(result) }}</pre>
      </div>
      <div v-else class="tool-section">
        <div class="section-label">执行中...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  name: { type: String, default: '' },
  args: { type: [Object, String], default: () => ({}) },
  result: { type: [Object, String, null], default: null },
  status: { type: String, default: 'running' }
})

const expanded = ref(false)

function toggle() {
  expanded.value = !expanded.value
}

const statusClass = computed(() => ({
  running: 'st-running',
  done: 'st-done',
  error: 'st-error'
}[props.status] || 'st-running'))

const statusText = computed(() => ({
  running: '执行中',
  done: '完成',
  error: '错误'
}[props.status] || '执行中'))

function formatJson(v) {
  if (typeof v === 'string') return v
  try {
    return JSON.stringify(v, null, 2)
  } catch (e) {
    return String(v)
  }
}
</script>

<style scoped>
.tool-block {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  margin: 8px 0;
  background: #fafafa;
  overflow: hidden;
}
.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
}
.tool-header:hover {
  background: #f0f0f0;
}
.tool-icon {
  font-size: 14px;
}
.tool-name {
  font-weight: 500;
  font-size: 13px;
  flex: 1;
}
.tool-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}
.st-running {
  background: #fff7e6;
  color: #fa8c16;
}
.st-done {
  background: #f6ffed;
  color: #52c41a;
}
.st-error {
  background: #fff1f0;
  color: #ff4d4f;
}
.toggle-icon {
  color: #999;
  font-size: 12px;
}
.tool-body {
  padding: 8px 12px 12px;
  border-top: 1px solid #f0f0f0;
}
.section-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
}
.tool-section {
  margin-bottom: 8px;
}
.tool-section pre {
  margin: 0;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow: auto;
}
</style>
