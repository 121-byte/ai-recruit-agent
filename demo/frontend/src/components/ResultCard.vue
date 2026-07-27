<template>
  <a-card class="result-card" :title="title" size="small" :bordered="true">
    <template #extra>
      <slot name="extra" />
    </template>
    <div v-if="loading" class="rc-loading">加载中...</div>
    <template v-else>
      <pre v-if="typeof data === 'string'" class="rc-text">{{ data }}</pre>
      <div v-else-if="data && typeof data === 'object'" class="rc-json">
        <pre>{{ formatJson(data) }}</pre>
      </div>
      <div v-else class="rc-empty">暂无数据</div>
    </template>
  </a-card>
</template>

<script setup>
defineProps({
  title: { type: String, default: '结果' },
  data: { type: [Object, String, Array, null], default: null },
  loading: { type: Boolean, default: false }
})

function formatJson(v) {
  try {
    return JSON.stringify(v, null, 2)
  } catch (e) {
    return String(v)
  }
}
</script>

<style scoped>
.result-card {
  margin: 8px 0;
}
.rc-text,
.rc-json pre {
  margin: 0;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
.rc-loading,
.rc-empty {
  text-align: center;
  color: #bbb;
  padding: 16px 0;
  font-size: 13px;
}
</style>
