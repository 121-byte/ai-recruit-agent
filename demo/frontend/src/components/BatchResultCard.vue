<template>
  <div class="batch-result-card">
    <div class="brc-title">📦 批量任务结果 ({{ results.length }})</div>
    <div class="brc-list">
      <div
        v-for="(item, idx) in results"
        :key="idx"
        class="brc-item"
        :class="{ ok: item.success, fail: !item.success }"
      >
        <span class="brc-status">{{ item.success ? '✓' : '✗' }}</span>
        <span class="brc-name">{{ item.name || item.id || `#${idx + 1}` }}</span>
        <span class="brc-detail">{{ item.summary || item.message || '' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  results: { type: Array, default: () => [] }
})
</script>

<style scoped>
.batch-result-card {
  border: 1px solid #e8e8e8;
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
}
.brc-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  color: #555;
}
.brc-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.brc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fff;
  border-radius: 4px;
  font-size: 12px;
}
.brc-item.ok {
  border-left: 3px solid #52c41a;
}
.brc-item.fail {
  border-left: 3px solid #ff4d4f;
}
.brc-status {
  font-weight: 700;
}
.brc-name {
  font-weight: 500;
}
.brc-detail {
  color: #888;
  flex: 1;
}
</style>
