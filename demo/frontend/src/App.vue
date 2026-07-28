<template>
  <a-config-provider :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { theme } from 'ant-design-vue'
import { useTheme } from '@/composables/useTheme'

const { isDark, apply } = useTheme()

const themeConfig = computed(() => ({
  algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
  token: {
    colorPrimary: '#b46a46',
    borderRadius: 14,
    fontFamily: "Inter, system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif",
    colorBgContainer: isDark.value ? '#25201d' : '#fff8f1',
    colorBgLayout: isDark.value ? '#1a1513' : '#f7eee6',
    colorTextBase: isDark.value ? '#e8e0da' : '#2b211c',
    colorBorder: isDark.value ? '#3d3530' : '#dac8b9',
    colorBorderSecondary: isDark.value ? '#2d2723' : '#eaded4',
    colorSuccess: isDark.value ? '#5aaa6a' : '#4d8f5a',
    colorWarning: isDark.value ? '#d9964a' : '#c88735',
    colorError: isDark.value ? '#cc5a5a' : '#b84c4c',
    colorInfo: '#b46a46',
    wireframe: false,
  },
}))

onMounted(() => {
  apply()
})
</script>
