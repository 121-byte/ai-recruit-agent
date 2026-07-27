<template>
  <div class="search-sources" v-if="sources && sources.length">
    <div class="ss-title">🌐 联网搜索来源</div>
    <div class="ss-list">
      <a
        v-for="(src, idx) in sources"
        :key="idx"
        :href="src.url || src.link"
        target="_blank"
        rel="noopener"
        class="ss-item"
      >
        <span class="ss-index">[{{ idx + 1 }}]</span>
        <span class="ss-title-text">{{ src.title || src.url }}</span>
        <span class="ss-link">{{ src.url || src.link }}</span>
      </a>
    </div>
  </div>
</template>

<script setup>
defineProps({
  sources: { type: Array, default: () => [] }
})
</script>

<style scoped>
.search-sources {
  border: 1px solid #d9d9d9;
  background: #fafafa;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 8px 0;
}
.ss-title {
  font-weight: 600;
  font-size: 12px;
  color: #555;
  margin-bottom: 6px;
}
.ss-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ss-item {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #1677ff;
  text-decoration: none;
}
.ss-item:hover {
  text-decoration: underline;
}
.ss-index {
  color: #999;
}
.ss-title-text {
  font-weight: 500;
}
.ss-link {
  color: #bbb;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}
</style>
