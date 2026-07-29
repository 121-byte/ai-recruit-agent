<template>
  <div class="thinking-wrap" :class="{ active }">
    <div class="thinking-head" @click="onToggle">
      <svg class="chevron" :class="{ open: expanded }" viewBox="0 0 24 24">
        <polyline points="9 18 15 12 9 6" />
      </svg>
      <span class="think-label">{{ active ? 'Agent 思考中' : '思考过程' }}</span>
      <span v-if="active" class="dots">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </span>
      <span v-else-if="reasoning" class="think-done">已完成</span>
    </div>
    <div v-show="expanded" class="thinking-body">
      <pre v-if="reasoning">{{ reasoning }}</pre>
      <span v-else class="empty">（暂无思考内容）</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  active: { type: Boolean, default: false },
  reasoning: { type: String, default: '' }
})

// 流式期间默认展开, 思考结束自动收起 (用户可再次点开)
const expanded = ref(true)

watch(
  () => props.active,
  (now, prev) => {
    if (prev && !now) expanded.value = false
    if (now) expanded.value = true
  }
)

function onToggle() {
  expanded.value = !expanded.value
}
</script>

<style scoped>
.thinking-wrap {
  margin: 4px 0 8px;
  border: 1px solid #d6e4ff;
  border-radius: 10px;
  background: linear-gradient(90deg, #f5f9ff, #faf5ff);
  overflow: hidden;
  font-size: 13px;
}
.thinking-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
  color: #1677ff;
}
.chevron {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: transform 0.2s;
  transform: rotate(0deg);
}
.chevron.open {
  transform: rotate(90deg);
}
.think-label {
  font-weight: 600;
}
.think-done {
  font-size: 12px;
  color: #8c8c8c;
}
.dots {
  display: inline-flex;
  gap: 4px;
  margin-left: 2px;
}
.dot {
  width: 5px;
  height: 5px;
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
.thinking-body {
  padding: 8px 12px 10px;
  border-top: 1px dashed #d6e4ff;
  background: #fff;
  max-height: 320px;
  overflow: auto;
}
.thinking-body pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: #595959;
}
.empty {
  color: var(--muted, #bfbfbf);
  font-size: 12px;
}
@keyframes thinking-dot {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
