<template>
  <MainLayout>
    <div class="chat-layout">
      <!-- 左侧会话列表 -->
      <div class="sidebar-wrap">
        <SessionSidebar
          :sessions="sessions"
          :active-id="sessionId"
          @select="onSelectSession"
          @new="onNewSession"
        />
      </div>

      <!-- 右侧对话区 -->
      <div class="chat-main">
        <div class="chat-header">
          <span class="chat-title">Agent 对话</span>
          <div class="chat-actions">
            <a-tag v-if="sending" color="processing">流式响应中</a-tag>
            <a-tag v-if="currentSessionTokens.total" color="blue">
              Tokens: {{ currentSessionTokens.total }}
            </a-tag>
            <a-button size="small" @click="showTrace = !showTrace">
              {{ showTrace ? '隐藏' : '显示' }}追踪
            </a-button>
            <a-button size="small" @click="clearMessages">清空</a-button>
          </div>
        </div>

        <!-- 追踪 / 统计面板 -->
        <div v-if="showTrace" class="trace-wrap">
          <TracePanel :trace="currentTrace" />
          <StatsBar :stats="turnStats" />
          <PlanTrack :plan="currentPlan" />
        </div>

        <!-- 消息流 -->
        <div class="messages" ref="messagesRef">
          <div v-if="!messages.length" class="empty-chat">
            <p>👋 你好！我是招聘 Agent，可以帮你：</p>
            <ul>
              <li>分析岗位需求并生成 JD</li>
              <li>筛选简历并匹配候选人</li>
              <li>生成面试问题与评估报告</li>
              <li>联网搜索行业信息</li>
            </ul>
            <p class="hint">在下方输入你的需求开始对话</p>
          </div>

          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="msg-item"
            :class="msg.role"
          >
            <div class="msg-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
            <div class="msg-content">
              <div class="msg-role">{{ msg.role === 'user' ? '我' : 'Agent' }}</div>
              <ThinkingBox v-if="msg.thinking" :active="true" />
              <PlanTrack v-if="msg.plan" :plan="msg.plan" />
              <div v-if="msg.content" class="msg-text" v-text="msg.content"></div>
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
            </div>
          </div>
        </div>

        <!-- HITL 卡片 -->
        <HitlCard
          v-if="pendingHitl"
          :data="pendingHitl"
          @confirm="(d) => resolveHitl(d, true)"
          @reject="(d) => resolveHitl(d, false)"
        />

        <!-- 推送消息 -->
        <PushMessage
          v-for="(pm, i) in pushMessages.slice(-3)"
          :key="`push-${i}`"
          :message="pm"
        />

        <!-- 底部输入框 -->
        <div class="input-area">
          <a-input
            v-model:value="inputText"
            placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
            :auto-size="{ minRows: 1, maxRows: 5 }"
            type="textarea"
            @pressEnter="onEnter"
            :disabled="sending"
          />
          <a-button
            type="primary"
            :loading="sending"
            :disabled="!inputText.trim()"
            @click="onSend"
          >
            发送
          </a-button>
          <a-button v-if="sending" danger @click="stop">停止</a-button>
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
import SessionSidebar from '@/components/SessionSidebar.vue'
import ThinkingBox from '@/components/ThinkingBox.vue'
import ToolBlock from '@/components/ToolBlock.vue'
import PlanTrack from '@/components/PlanTrack.vue'
import HitlCard from '@/components/HitlCard.vue'
import TracePanel from '@/components/TracePanel.vue'
import StatsBar from '@/components/StatsBar.vue'
import PushMessage from '@/components/PushMessage.vue'

const {
  messages,
  sending,
  sessionId,
  sessions,
  currentPlan,
  pendingHitl,
  currentTrace,
  pushMessages,
  turnStats,
  currentSessionTokens,
  send,
  stop,
  resolveHitl,
  loadSessions,
  newSession,
  selectSession,
  clearMessages
} = useAgentStream()

const inputText = ref('')
const showTrace = ref(false)
const messagesRef = ref(null)

async function onSend() {
  const text = inputText.value
  inputText.value = ''
  await send(text)
}

function onEnter(e) {
  // Shift+Enter 换行，Enter 发送
  if (e.shiftKey) return
  e.preventDefault()
  onSend()
}

function onSelectSession(sid) {
  selectSession(sid)
}

async function onNewSession() {
  await newSession('新会话')
  message.success('已创建新会话')
}

// 自动滚动到底部
watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)

// 流式内容更新时也滚动
watch(
  () => messages.value.map((m) => m.content).join(''),
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 56px);
}
.sidebar-wrap {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}
.chat-title {
  font-weight: 600;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.trace-wrap {
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f0f2f5;
}
.empty-chat {
  text-align: center;
  color: #888;
  margin-top: 80px;
}
.empty-chat ul {
  text-align: left;
  display: inline-block;
}
.empty-chat .hint {
  color: #1677ff;
}
.msg-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.msg-item.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  font-size: 22px;
  flex-shrink: 0;
}
.msg-content {
  max-width: 75%;
}
.msg-item.user .msg-content {
  text-align: right;
}
.msg-role {
  font-size: 11px;
  color: #999;
  margin-bottom: 4px;
}
.msg-text {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  background: #fff;
  border: 1px solid #f0f0f0;
}
.msg-item.user .msg-text {
  background: #1677ff;
  color: #fff;
}
.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  align-items: flex-end;
}
.input-area :deep(.ant-input) {
  flex: 1;
}
</style>
