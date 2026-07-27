<template>
  <a-layout class="main-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :trigger="null" width="220">
      <div class="logo">
        <span class="logo-text">{{ collapsed ? 'AI' : 'AI 招聘系统' }}</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        theme="light"
        @click="onMenuClick"
      >
        <a-menu-item key="/">
          <span>📊 仪表盘</span>
        </a-menu-item>
        <a-menu-item key="/chat">
          <span>💬 Agent 对话</span>
        </a-menu-item>
        <a-menu-item key="/jobs">
          <span>💼 岗位管理</span>
        </a-menu-item>
        <a-menu-item key="/resumes">
          <span>📄 简历管理</span>
        </a-menu-item>
        <a-menu-item key="/matches">
          <span>🎯 候选人匹配</span>
        </a-menu-item>
        <a-menu-item key="/interviews">
          <span>📅 面试管理</span>
        </a-menu-item>
        <a-menu-item key="/interview-agent">
          <span>🤖 AI 面试官</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <div class="header-left">
          <a-button type="text" @click="collapsed = !collapsed">
            <span class="collapse-icon">{{ collapsed ? '☰' : '✕' }}</span>
          </a-button>
        </div>
        <div class="header-right">
          <span class="user-name">{{ authStore.username || '用户' }}</span>
          <a-tag v-if="authStore.role" color="blue">{{ authStore.role }}</a-tag>
          <a-button type="link" danger @click="handleLogout">退出</a-button>
        </div>
      </a-layout-header>
      <a-layout-content class="content">
        <slot />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const collapsed = ref(false)
const selectedKeys = ref([route.path])

watch(
  () => route.path,
  (p) => {
    selectedKeys.value = [p]
  }
)

function onMenuClick({ key }) {
  router.push(key)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #001529;
  color: #fff;
}
.logo-text {
  font-size: 16px;
  font-weight: 700;
}
:deep(.ant-layout-sider) {
  background: #fff;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 0 16px;
  border-bottom: 1px solid #f0f0f0;
  height: 56px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-name {
  font-weight: 500;
  font-size: 14px;
}
.content {
  margin: 0;
  background: #f0f2f5;
  overflow: auto;
}
</style>
