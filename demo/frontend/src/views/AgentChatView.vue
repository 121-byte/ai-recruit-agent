<template>
  <MainLayout>
    <div class="chat-agent-wrap">
      <!-- 会话侧边栏 -->
      <aside class="chat-sidebar">
        <div class="chat-sidebar-head">
          <h4>会话历史</h4>
          <span class="session-count">{{ sessions.length }}</span>
        </div>
        <div class="chat-sidebar-list">
          <div
            v-for="s in sessions"
            :key="s.id || s.sessionId"
            class="chat-sidebar-item"
            :class="{ active: String(sessionId) === String(s.id || s.sessionId) }"
            @click="onSelectSession(s.id || s.sessionId)"
          >
            <div class="sess-icon">💬</div>
            <div class="sess-info">
              <div class="sess-title">{{ s.title || s.name || '未命名会话' }}</div>
              <div class="sess-meta">{{ s.messageCount != null ? s.messageCount + ' 条' : '' }}{{ s.updatedAt ? ' · ' + s.updatedAt : '' }}</div>
            </div>
            <span v-if="s.tokenCount != null" class="sess-token">{{ tokenShort(s.tokenCount) }}</span>
          </div>
          <div v-if="!sessions.length" style="padding:16px;text-align:center;color:var(--muted);font-size:12px">
            暂无历史会话
          </div>
        </div>
        <div class="chat-sidebar-footer">
          <div class="all-token-summary" v-if="sessions.length">
            全部会话 Tokens：<b>{{ allSessionsTokens.toLocaleString() }}</b>
            <span class="sub">· {{ sessions.length }} 个会话</span>
          </div>
          <button class="new-chat-btn" @click="onNewSession">
            <svg viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            新建会话
          </button>
        </div>
      </aside>

      <!-- 主对话区 -->
      <div class="chat-layout agent">
        <div class="chat-header">
          <div class="chat-header-info">
            <span class="status-dot"></span>
            <div>
              <h3>Agent 助手</h3>
              <span class="status-text">{{ sending ? '流式响应中…' : '在线 · 随时为您服务' }}</span>
            </div>
          </div>
          <div style="display:flex;align-items:center;gap:var(--space-3)">
            <span v-if="currentSessionTokens.total" class="token-badge">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              {{ currentSessionTokens.total.toLocaleString() }}
            </span>
            <a-button size="small" @click="showTrace = !showTrace">
              {{ showTrace ? '隐藏' : '显示' }}追踪
            </a-button>
            <button class="btn btn-secondary" @click="clearMessages">清空</button>
          </div>
        </div>

        <!-- 追踪 / 统计面板 -->
        <div v-if="showTrace" class="interview-config" style="flex-direction:column;align-items:stretch;background:var(--bg)">
          <TracePanel :trace="currentTrace" />
          <StatsBar :stats="turnStats" />
          <PlanTrack :plan="currentPlan" />
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div v-if="!messages.length" class="chat-msg bot">
            <div class="chat-msg-avatar">🤖</div>
            <div>
              <div class="chat-msg-bubble">
                你好！我是 AI 招聘系统的智能助手。我可以帮你：<br />
                • 分析岗位需求和简历匹配度<br />
                • 推荐适合岗位的候选人<br />
                • 生成面试题目和评分标准<br />
                • 汇总面试反馈和招聘数据
              </div>
              <div class="chat-msg-time">刚刚</div>
            </div>
          </div>

          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="chat-msg"
            :class="msg.role"
          >
            <div class="chat-msg-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
            <div style="max-width:100%">
              <ThinkingBox v-if="msg.thinking" :active="true" />
              <PlanTrack v-if="msg.plan" :plan="msg.plan" />
              <div v-if="msg.content" class="chat-msg-bubble">{{ msg.content }}</div>
              <ToolBlock
                v-for="(tc, i) in msg.toolCalls || []"
                :key="i"
                :name="tc.name"
                :args="tc.args"
                :result="tc.result"
                :status="tc.status"
              />
              <TracePanel v-if="msg.trace" :trace="msg.trace" />
              <StatsBar v-if="msg.stats" :stats="msg.stats" />
              <!-- 每轮 Token 标志: 点击展开消耗明细 -->
              <div v-if="msg.role === 'assistant' && msg.tokens && msg.tokens.total"
                   class="turn-token-wrap">
                <span class="turn-token-chip" @click="msg.showTokens = !msg.showTokens">
                  <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                  {{ tokenShort(msg.tokens.total) }} tokens
                  <span v-if="msg.tokens.estimated" class="est-tag">估算</span>
                </span>
                <div v-if="msg.showTokens" class="turn-token-detail">
                  <div class="row"><span>输入</span><b>{{ (msg.tokens.input ?? 0).toLocaleString() }}</b></div>
                  <div class="row"><span>输出</span><b>{{ (msg.tokens.output ?? 0).toLocaleString() }}</b></div>
                  <div class="row total"><span>合计</span><b>{{ (msg.tokens.total ?? 0).toLocaleString() }}</b></div>
                  <span v-if="msg.tokens.estimated" class="est-flag">* 字符估算值</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 当前会话累计 Token (会话历史底部) -->
          <div v-if="currentSessionTokens.total" class="session-token-footer">
            本会话累计 Tokens：<b>{{ currentSessionTokens.total.toLocaleString() }}</b>
            <span class="sub">输入 {{ (currentSessionTokens.input ?? 0).toLocaleString() }} / 输出 {{ (currentSessionTokens.output ?? 0).toLocaleString() }}</span>
          </div>
        </div>

        <!-- HITL 卡片 -->
        <HitlCard
          v-if="pendingHitl"
          :data="pendingHitl"
          @confirm="(d) => resolveHitl(d, true)"
          @reject="(d) => resolveHitl(d, false)"
        />

        <div class="chat-input-area">
          <a-input
            v-model:value="inputText"
            placeholder="输入您的问题，例如「帮我分析 Java 工程师的候选人」…"
            :auto-size="{ minRows: 1, maxRows: 5 }"
            type="textarea"
            @pressEnter="onEnter"
            :disabled="sending"
          />
          <button v-if="sending" class="btn btn-secondary" style="color:var(--danger)" @click="stop">停止</button>
          <button class="btn btn-primary" :disabled="sending || !inputText.trim()" @click="onSend">
            <svg width="14" height="14" viewBox="0 0 24 24"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            发送
          </button>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useAgentStream } from '@/composables/useAgentStream'
import MainLayout from '@/components/MainLayout.vue'
import ThinkingBox from '@/components/ThinkingBox.vue'
import ToolBlock from '@/components/ToolBlock.vue'
import PlanTrack from '@/components/PlanTrack.vue'
import HitlCard from '@/components/HitlCard.vue'
import TracePanel from '@/components/TracePanel.vue'
import StatsBar from '@/components/StatsBar.vue'

const {
  messages, sending, sessionId, sessions,
  currentPlan, pendingHitl, currentTrace, turnStats, currentSessionTokens,
  allSessionsTokens,
  send, stop, resolveHitl, loadSessions, newSession, selectSession, clearMessages,
} = useAgentStream()

const inputText = ref('')
const showTrace = ref(false)
const messagesRef = ref(null)

function tokenShort(n) {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

async function onSend() {
  const text = inputText.value
  if (!text.trim()) return
  inputText.value = ''
  await send(text)
}

function onEnter(e) {
  if (e.shiftKey) return
  e.preventDefault()
  onSend()
}

function onSelectSession(sid) {
  selectSession(sid)
}async function onNewSession() {
  await newSession('新会话')
  message.success('已创建新会话')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value.map((m) => m.content).join(''), scrollToBottom)

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.chat-input-area :deep(.ant-input) {
  flex: 1;
  background: var(--bg) !important;
}
</style>
