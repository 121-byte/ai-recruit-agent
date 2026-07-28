<template>
  <span>{{ display }}</span>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'

const props = defineProps({
  target: { type: Number, default: 0 },
  duration: { type: Number, default: 1200 },
})
const current = ref(0)
const display = ref('0')

function format(n) {
  return Math.round(n).toLocaleString()
}

function animate(to) {
  if (!to || to <= 0) {
    current.value = 0
    display.value = '0'
    return
  }
  const start = current.value
  const delta = to - start
  if (delta === 0) return
  const t0 = performance.now()
  const dur = props.duration

  function step(now) {
    const p = Math.min(1, (now - t0) / dur)
    // easeOutCubic
    const e = 1 - Math.pow(1 - p, 3)
    current.value = start + delta * e
    display.value = format(current.value)
    if (p < 1) requestAnimationFrame(step)
    else {
      current.value = to
      display.value = format(to)
    }
  }
  requestAnimationFrame(step)
}

onMounted(() => animate(Number(props.target) || 0))
watch(() => props.target, (v) => animate(Number(v) || 0))
</script>
