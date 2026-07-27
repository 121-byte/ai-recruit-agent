<template>
  <div class="trace-panel" v-if="trace">
    <div class="trace-title">🔍 LLM 调用追踪</div>
    <div class="trace-grid">
      <div class="trace-item">
        <span class="label">模型</span>
        <span class="value">{{ trace.model || trace.modelName || '-' }}</span>
      </div>
      <div class="trace-item">
        <span class="label">Prompt Tokens</span>
        <span class="value">{{ trace.promptTokens || trace.tokens?.prompt || '-' }}</span>
      </div>
      <div class="trace-item">
        <span class="label">Completion Tokens</span>
        <span class="value">{{ trace.completionTokens || trace.tokens?.completion || '-' }}</span>
      </div>
      <div class="trace-item">
        <span class="label">总 Tokens</span>
        <span class="value">{{ trace.totalTokens || trace.tokens?.total || '-' }}</span>
      </div>
      <div class="trace-item">
        <span class="label">耗时</span>
        <span class="value">{{ formatLatency(trace.latency || trace.latencyMs) }}</span>
      </div>
      <div class="trace-item" v-if="trace.cost">
        <span class="label">费用</span>
        <span class="value">¥{{ trace.cost }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  trace: { type: Object, default: () => null }
})

function formatLatency(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
</script>

<style scoped>
.trace-panel {
  border: 1px solid #e8e8e8;
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
  font-size: 12px;
}
.trace-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #555;
}
.trace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
}
.trace-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.label {
  color: #999;
  font-size: 11px;
}
.value {
  font-weight: 600;
  color: #333;
}
</style>
