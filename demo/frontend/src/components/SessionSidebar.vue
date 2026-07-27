<template>
  <div class="session-sidebar">
    <div class="sidebar-header">
      <span class="title">会话列表</span>
      <a-button type="primary" size="small" @click="emit('new')">+ 新建</a-button>
    </div>
    <div class="sidebar-list">
      <div
        v-for="s in sessions"
        :key="s.sessionId"
        class="session-item"
        :class="{ active: s.sessionId === activeId }"
        @click="emit('select', s.sessionId)"
      >
        <div class="session-title">{{ s.title || s.sessionId }}</div>
        <div class="session-meta">
          <span class="meta-time">{{ formatTime(s.updatedAt || s.createTime) }}</span>
        </div>
      </div>
      <div v-if="!sessions.length" class="empty-tip">暂无会话</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

const emit = defineEmits(['select', 'new'])

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch (e) {
    return ''
  }
}
</script>

<style scoped>
.session-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-right: 1px solid #f0f0f0;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.title {
  font-weight: 600;
  font-size: 14px;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.session-item:hover {
  background: #f0f7ff;
}
.session-item.active {
  background: #e6f4ff;
  border-left: 3px solid #1677ff;
}
.session-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-time {
  font-size: 11px;
  color: #999;
}
.empty-tip {
  text-align: center;
  color: #bbb;
  font-size: 12px;
  padding: 24px 0;
}
</style>
