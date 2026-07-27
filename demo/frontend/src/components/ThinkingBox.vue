<template>
  <div class="thinking-box" :class="{ active }">
    <span class="think-label">Agent 思考中</span>
    <span class="dots">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </span>
  </div>
</template>

<script setup>
defineProps({
  active: { type: Boolean, default: false }
})
</script>

<style scoped>
.thinking-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  background: linear-gradient(90deg, #e6f4ff, #f9f0ff);
  border: 1px solid #d6e4ff;
  border-radius: 16px;
  font-size: 13px;
  color: #1677ff;
}
.think-label {
  font-weight: 500;
}
.dots {
  display: inline-flex;
  gap: 4px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1677ff;
  animation: thinking-dot 1.4s infinite ease-in-out both;
}
.dot:nth-child(2) {
  animation-delay: 0.16s;
}
.dot:nth-child(3) {
  animation-delay: 0.32s;
}
</style>
