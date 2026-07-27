<template>
  <div class="stats-bar" v-if="stats">
    <div class="stat-item">
      <span class="stat-label">工具调用</span>
      <span class="stat-value">{{ stats.toolCalls ?? stats.tool_calls ?? 0 }}</span>
    </div>
    <div class="stat-item">
      <span class="stat-label">Tokens</span>
      <span class="stat-value">{{ stats.totalTokens ?? stats.tokens?.total ?? 0 }}</span>
    </div>
    <div class="stat-item">
      <span class="stat-label">耗时</span>
      <span class="stat-value">{{ formatLatency(stats.latency ?? stats.latencyMs) }}</span>
    </div>
    <div class="stat-item" v-if="stats.steps !== undefined">
      <span class="stat-label">步数</span>
      <span class="stat-value">{{ stats.steps }}</span>
    </div>
    <div class="stat-item" v-if="stats.cost !== undefined">
      <span class="stat-label">费用</span>
      <span class="stat-value">¥{{ stats.cost }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  stats: { type: Object, default: null }
})

function formatLatency(ms) {
  if (ms === undefined || ms === null) return '0ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
</script>

<style scoped>
.stats-bar {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
  margin: 8px 0;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 80px;
}
.stat-label {
  font-size: 11px;
  color: #999;
}
.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #1677ff;
}
</style>
