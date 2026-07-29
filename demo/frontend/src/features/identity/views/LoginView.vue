<template>
  <div class="screen-login">
    <div class="login-card">
      <div class="login-brand">
        <div class="logo-badge">AI</div>
        <h1>AI 智能招聘</h1>
        <p>Agent 驱动 · 智能匹配 · 高效面试</p>
      </div>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="login-user">用户名</label>
          <input
            id="login-user"
            v-model="form.username"
            class="form-input"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </div>
        <div class="form-group">
          <label for="login-pass">密码</label>
          <input
            id="login-pass"
            v-model="form.password"
            class="form-input"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </div>
        <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
          <svg width="18" height="18" viewBox="0 0 24 24"><path d="M12 16v-4"/><path d="M12 8h.01"/><path d="M22 12c0 5.523-4.477 10-10 10S2 17.523 2 12 6.477 2 12 2s10 4.477 10 10z"/></svg>
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <div class="ql-divider"><span>快捷登录（演示用）</span></div>

      <div class="ql-row">
        <button class="ql-btn hr" @click="quickLogin('hr_user')" :disabled="loading">
          <span class="ql-icon">👤</span>
          <span class="ql-info">
            <span class="ql-role">HR 招聘负责人</span>
            <span class="ql-user">hr_user / 123456</span>
          </span>
          <span class="ql-arrow">→</span>
        </button>
        <button class="ql-btn ops" @click="quickLogin('ops_user')" :disabled="loading">
          <span class="ql-icon">🔧</span>
          <span class="ql-info">
            <span class="ql-role">运营人员</span>
            <span class="ql-user">ops_user / 123456</span>
          </span>
          <span class="ql-arrow">→</span>
        </button>
      </div>

      <p class="login-footer-text">提示：两种角色可查看不同的界面和功能</p>
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
  if (!form.username || !form.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form.username, form.password)
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

async function quickLogin(username) {
  const password = '123456'
  loading.value = true
  try {
    const res = await login(username, password)
    const token = res.token || res.tokenInfo?.tokenValue || res.satoken
    const user = res.user || res.userInfo || { username }
    authStore.login(token, user)
    message.success(`以 ${user.realName || username} 身份登录成功`)
    router.push('/')
  } catch (e) {
    message.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 快捷登录按钮 ── Claymorphism 风格 */
.ql-divider {
  display: flex; align-items: center; gap: var(--space-3);
  margin: var(--space-6) 0 var(--space-4);
  color: var(--muted); font-size: var(--text-xs); font-weight: 500;
}
.ql-divider::before,
.ql-divider::after {
  content: ''; flex: 1; height: 1px; background: var(--border-soft);
}
.ql-divider span { white-space: nowrap; }

.ql-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }

.ql-btn {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  cursor: pointer;
  transition: all var(--motion-fast) var(--ease-standard);
  text-align: left;
  font-family: var(--font-body);
  box-shadow: var(--elev-ring);
  position: relative;
  overflow: hidden;
}
.ql-btn:active { transform: scale(0.97); }
.ql-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.ql-btn::before {
  content: ''; position: absolute; inset: 0;
  opacity: 0; transition: opacity var(--motion-fast);
  border-radius: inherit;
}
.ql-btn.hr::before { background: linear-gradient(135deg, rgba(180,106,70,0.08), rgba(180,106,70,0.02)); }
.ql-btn.ops::before { background: linear-gradient(135deg, rgba(75,85,150,0.08), rgba(75,85,150,0.02)); }
.ql-btn:hover { border-color: var(--accent); box-shadow: var(--elev-raised); transform: translateY(-1px); }
.ql-btn:hover::before { opacity: 1; }

.ql-icon { font-size: 20px; line-height: 1; flex-shrink: 0; }
.ql-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.ql-role { font-size: var(--text-sm); font-weight: 700; color: var(--fg); line-height: 1.2; }
.ql-user { font-size: 11px; color: var(--muted); line-height: 1.2; }
.ql-arrow {
  font-size: 14px; color: var(--border);
  transition: transform var(--motion-fast), color var(--motion-fast);
  flex-shrink: 0;
}
.ql-btn:hover .ql-arrow { color: var(--accent); transform: translateX(3px); }

.ql-btn.hr:hover { border-color: var(--accent); }
.ql-btn.ops:hover { border-color: #4b5596; }
.ql-btn.ops:hover .ql-arrow { color: #4b5596; }
</style>
