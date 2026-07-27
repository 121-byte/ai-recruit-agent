<template>
  <a-config-provider :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { computed } from 'vue'

const themeConfig = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 8
  }
}
</script>
