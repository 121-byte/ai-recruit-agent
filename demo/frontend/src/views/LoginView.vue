<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-title">
        <h1>AI 招聘系统</h1>
        <p>智能招聘 · Agent 驱动</p>
      </div>
      <a-form :model="form" layout="vertical" @finish="handleLogin">
        <a-form-item
          label="用户名"
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input v-model:value="form.username" placeholder="请输入用户名" size="large">
            <template #prefix><span>👤</span></template>
          </a-input>
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
            v-model:value="form.password"
            placeholder="请输入密码"
            size="large"
            @pressEnter="handleLogin"
          >
            <template #prefix><span>🔒</span></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
          >
            登录
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { login } from '@/api'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    // 后端返回 { token, user } 或 { tokenInfo, ... }
    const token = res.token || res.tokenInfo?.tokenValue || res.satoken
    const user = res.user || res.userInfo || { username: form.username }
    authStore.login(token, user)
    message.success('登录成功')
    router.push('/')
  } catch (e) {
    message.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
}
.login-title h1 {
  margin: 0;
  font-size: 24px;
  color: #1677ff;
}
.login-title p {
  margin: 8px 0 0;
  color: #999;
  font-size: 13px;
}
</style>
