<template>
  <MainLayout>
    <div class="chat-layout stretch">
      <div class="chat-header">
        <div class="chat-header-info">
          <span class="status-dot"></span>
          <div>
            <h3>AI 面试官</h3>
            <span class="status-text">{{ sending ? '面试进行中…' : '在线 · 随时可以开始面试' }}</span>
          </div>
        </div>
        <div style="display:flex;gap:var(--space-2);align-items:center;flex-wrap:wrap">
          <span class="config-label" style="font-size:var(--text-sm);color:var(--fg-2)">难度：</span>
          <a-select
            v-model:value="difficulty"
            style="width:110px"
            :options="difficultyOptions"
            @change="onDifficultyChange"
          />
          <button class="btn btn-primary" @click="toggleStart" :disabled="sending">
            <svg width="14" height="14" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"/></svg>
            {{ messages.length ? '继续' : '开始面试' }}
          </button>
          <button v-if="report" class="btn btn-secondary" @click="report = ''" style="color:var(--muted)">收起报告</button>
          <button class="btn btn-secondary" :disabled="reportLoading || !interviewId" @click="generateReport">
            {{ reportLoading ? '生成中…' : '生成报告' }}
          </button>
        </div>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <div v-if="!messages.length" class="chat-msg bot">
          <div class="chat-msg-avatar">🤖</div>
          <div>
            <div class="chat-msg-bubble">你好！我是 AI 面试官。点击「开始面试」，我将根据岗位要求和候选人背景自动提问。</div>
            <div class="chat-msg-time">准备就绪</div>
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
            <div v-if="msg.content" class="chat-msg-bubble">{{ msg.content }}</div>
          </div>
        </div>
      </div>

      <div v-if="report" class="interview-config" style="background:var(--surface);border-top:1px solid var(--border-soft);border-bottom:none;max-height:240px;overflow-y:auto">
        <pre style="margin:0;font-size:13px;white-space:pre-wrap;font-family:var(--font-mono);color:var(--fg-2)">{{ report }}</pre>
      </div>

      <div class="chat-input-area">
        <input
          v-model="inputText"
          type="text"
          :placeholder="messages.length ? '输入回答，Enter 发送…' : '点击「开始面试」启动面试'"
          :disabled="sending"
          @keyup.enter="onSend"
        />
        <button v-if="sessionId && !sending" class="btn btn-secondary" style="color:var(--danger)" @click="endInterview">结束</button>
        <button class="btn btn-primary" :disabled="sending || !inputText.trim()" @click="onSend">
          <svg width="14" height="14" viewBox="0 0 24 24"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          发送
        </button>
      </div>
    </div>
  </MainLayout>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { interviewAgentStart, interviewAgentAnswer, interviewAgentEnd, generateInterviewReport } from '@/api'
import MainLayout from '@/components/MainLayout.vue'
import ThinkingBox from '@/components/ThinkingBox.vue'

const route = useRoute()
const interviewId = ref(route.query.interviewId || '')
const candidateName = ref('')

const messages = ref([])
const sending = ref(false)
const sessionId = ref(null)
const inputText = ref('')
const difficulty = ref('medium')
const report = ref('')
const reportLoading = ref(false)
const messagesRef = ref(null)

const difficultyOptions = [
  { label: '简单', value: 'easy' },
  { label: '中等', value: 'medium' },
  { label: '困难', value: 'hard' },
  { label: '专家', value: 'expert' },
]

async function toggleStart() {
  if (!interviewId.value) {
    message.warning('请先从「面试管理」选择或安排一场面试')
    return
  }
  if (sessionId.value) {
    messages.value.push({ role: 'bot', content: '我们继续。请直接回答上一题，或输入你希望我重复的问题。' })
    return
  }
  sending.value = true
  try {
    const data = await interviewAgentStart(interviewId.value, { difficulty: difficulty.value })
    if (data?.error) throw new Error(data.error)
    sessionId.value = data.session_id || data.sessionId
    difficulty.value = data.difficulty || difficulty.value
    messages.value.push({
      role: 'bot',
      content: data.opening || '你好，我们开始今天的面试。请先做一个简短自我介绍。',
    })
  } catch (e) {
    message.error(e.message || '启动面试失败')
  } finally {
    sending.value = false
  }
}

async function onSend() {
  const text = inputText.value
  if (!text.trim()) return
  if (!sessionId.value) {
    await toggleStart()
    if (!sessionId.value) return
  }
  inputText.value = ''
  messages.value.push({ role: 'user', content: text.trim() })
  sending.value = true
  const bot = { role: 'bot', content: '', thinking: true }
  messages.value.push(bot)
  try {
    const data = await interviewAgentAnswer(sessionId.value, {
      answer: text.trim(),
      difficulty: difficulty.value,
    })
    if (data?.error) throw new Error(data.error)
    const parts = []
    if (data.feedback) parts.push(`反馈：${data.feedback}`)
    if (data.score != null) parts.push(`评分：${data.score}`)
    if (data.next) parts.push(data.next)
    bot.content = parts.join('\n\n') || '收到，我们继续下一题。'
  } catch (e) {
    bot.content = `[错误] ${e.message || '提交回答失败'}`
    message.error(e.message || '提交回答失败')
  } finally {
    bot.thinking = false
    sending.value = false
  }
}

function onDifficultyChange(val) {
  if (messages.value.length) {
    messages.value.push({ role: 'bot', content: `后续题目难度将调整为：${val}` })
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

async function endInterview() {
  if (!sessionId.value) return
  try {
    await interviewAgentEnd(sessionId.value)
    messages.value.push({ role: 'bot', content: '本轮面试已结束，可以生成报告查看总结。' })
    message.success('面试已结束')
  } catch (e) {
    message.error(e.message || '结束面试失败')
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value.map((m) => m.content).join(''), scrollToBottom)

onMounted(() => {
  if (route.query.candidateName) candidateName.value = route.query.candidateName
})
</script>
