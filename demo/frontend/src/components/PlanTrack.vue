<template>
  <div class="plan-track" v-if="plan && plan.length">
    <div class="plan-title">📋 执行规划</div>
    <div class="plan-steps">
      <div
        v-for="(step, idx) in plan"
        :key="idx"
        class="plan-step"
        :class="{ done: step.status === 'done', running: step.status === 'running' }"
      >
        <span class="step-index">{{ idx + 1 }}</span>
        <span class="step-text">{{ step.task || step.description || step.name }}</span>
        <span class="step-status">{{ step.status || 'pending' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  plan: { type: Array, default: () => [] }
})
</script>

<style scoped>
.plan-track {
  border: 1px solid #d6e4ff;
  background: #f0f7ff;
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
}
.plan-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
  color: #1677ff;
}
.plan-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.plan-step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #e8e8e8;
  font-size: 11px;
  color: #666;
  flex-shrink: 0;
}
.plan-step.done .step-index {
  background: #52c41a;
  color: #fff;
}
.plan-step.running .step-index {
  background: #1677ff;
  color: #fff;
}
.step-text {
  flex: 1;
}
.step-status {
  font-size: 11px;
  color: #999;
  text-transform: capitalize;
}
</style>
