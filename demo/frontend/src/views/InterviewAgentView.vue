<template>
  <MainLayout>
    <div class="ia-layout">
      <!-- 顶部控制栏 -->
      <div class="ia-topbar">
        <div class="ia-info">
          <span class="ia-title">AI 面试官</span>
          <a-tag v-if="interviewId" color="blue">面试 #{{ interviewId }}</a-tag>
          <a-tag v-if="candidateName" color="purple">{{ candidateName }}</a-tag>
        </div>
        <div class="ia-controls">
          <span class="ctrl-label">难度：</span>
          <a-select
            v-model:value="difficulty"
            style="width: 120px"
            :options="difficultyOptions"
            @change="onDifficultyChange"
          />
          <a-button type="primary" :loading="reportLoading" @click="generateReport" :disabled="!messages.length">
            生成报告
          </a-button>
        </div>
      </div>

      <!-- 对话区 -->
      <div class="ia-chat">
        <div class="messages" ref="messagesRef">
          <div v-if="!messages.length" class="empty">
            <p>🎤 AI 面试官已就绪</p>
            <p class="hint">点击下方"开始面试"按钮，AI 将根据候选人简历自动提问</p>
          </div>
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="msg-item"
            :class="msg.role"
          >
            <div class="msg-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
            <div class="msg-content">
              <div class="msg-role">{{ msg.role === 'user' ? '候选人' : '面试官' }}</div>
              <ThinkingBox v-if="msg.thinking" :active="true" />
              <div v-if="msg.content" class="msg-text">{{ msg.content }}</div>
              <TracePanel v-if="msg.trace" :trace="msg.trace" />
            </div>
          </div>
        </div>

        <!-- 报告展示 -->
        <div v-if="report" class="report-area">
          <a-card title="面试报告" size="small">
            <pre class="report-pre">{{ report }}</pre>
          </a-card>
        </div>

        <!-- 底部输入 -->
        <div class="input-area">
          <a-input
            v-model:value="inputText"
            placeholder="输入回答，Enter 发送"
            @pressEnter="onSend"
            :disabled="sending"
          />
          <a-button v-if="!messages.length" type="primary" @click="startInterview" :loading="sending">
            开始面试
          </a-button>
          <a-button v-else type="primary" @click="onSend" :loading="sending" :disabled="!inputText.trim()">
            发送
          </a-button>
          <a-button v-if="sending" danger @click="stop">停止</a-button>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAgentStream } from '@/composables/useAgentStream'
import { generateInterviewReport } from '@/api'
import MainLayout from '@/components/MainLayout.vue'
import ThinkingBox from '@/components/ThinkingBox.vue'
import TracePanel from '@/components/TracePanel.vue'

const route = useRoute()
const interviewId = ref(route.query.interviewId || '')
const candidateName = ref('')

const {
  messages,
  sending,
  sessionId,
  send,
  stop
} = useAgentStream()

const inputText = ref('')
const difficulty = ref('medium')
const report = ref('')
const reportLoading = ref(false)
const messagesRef = ref(null)

const difficultyOptions = [
  { label: '简单', value: 'easy' },
  { label: '中等', value: 'medium' },
  { label: '困难', value: 'hard' },
  { label: '专家', value: 'expert' }
]

async function startInterview() {
  sessionId.value = `interview-${interviewId.value || Date.now()}`
  await send(
    `请作为 AI 面试官开始一场面试。${candidateName.value ? '候选人：' + candidateName.value : ''}难度：${difficulty.value}。请先做一个简短的自我介绍并开始第一题。`
  )
}

async function onSend() {
  const text = inputText.value
  inputText.value = ''
  await send(text)
}

function onDifficultyChange(val) {
  message.info(`难度调整为：${difficultyOptions.find((d) => d.value === val)?.label}`)
  if (messages.value.length) {
    send(`[系统] 难度调整为 ${val}，请相应调整后续题目难度。`)
  }
}

async function generateReport() {
  reportLoading.value = true
  try {
    const data = await generateInterviewReport(interviewId.value)
    report.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    message.success('报告生成成功')
  } catch (e) {
    message.error(e.message || '报告生成失败')
  } finally {
    reportLoading.value = false
  }
}

watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }
)

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
  if (route.query.candidateName) {
    candidateName.value = route.query.candidateName
  }
})
</script>

<style scoped>
.ia-layout {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
}
.ia-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}
.ia-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ia-title {
  font-weight: 600;
  font-size: 15px;
}
.ia-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ctrl-label {
  font-size: 13px;
}
.ia-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f0f2f5;
}
.empty {
  text-align: center;
  color: #888;
  margin-top: 80px;
}
.empty .hint {
  color: #1677ff;
  font-size: 13px;
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
.report-area {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  max-height: 240px;
  overflow-y: auto;
}
.report-pre {
  margin: 0;
  font-size: 13px;
  white-space: pre-wrap;
}
.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.input-area :deep(.ant-input) {
  flex: 1;
}
</style>
