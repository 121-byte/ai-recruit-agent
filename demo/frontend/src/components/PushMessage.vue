<template>
  <transition name="push">
    <div v-if="message" class="push-message">
      <span class="push-icon">🔔</span>
      <div class="push-content">
        <div class="push-title">{{ message.title || '通知' }}</div>
        <div class="push-body">{{ message.content || message.body || message.message }}</div>
      </div>
    </div>
  </transition>
</template>

<script setup>
defineProps({
  message: { type: Object, default: null }
})
</script>

<style scoped>
.push-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  margin: 8px 0;
}
.push-icon {
  font-size: 16px;
}
.push-title {
  font-weight: 600;
  font-size: 13px;
  color: #389e0d;
}
.push-body {
  font-size: 12px;
  color: #555;
  margin-top: 2px;
}
.push-enter-active,
.push-leave-active {
  transition: all 0.3s;
}
.push-enter-from,
.push-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
