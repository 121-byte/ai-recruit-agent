import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'
import {
  listSessions,
  createSession,
  deleteSession,
  listSessionMessages,
  getSessionTokens
} from '@/api'

/**
 * Agent SSE 流处理组合式函数（§12.3）
 * 处理 POST /api/agent/chat/stream 的 SSE 事件流。
 * SSE 帧格式：event: <type>\ndata: <json>\n\n
 */
export function useAgentStream() {
  const authStore = useAuthStore()

  // ===== 响应式状态 =====
  const messages = ref([]) // 消息列表
  const sending = ref(false) // 发送中状态
  const sessionId = ref('default') // 当前会话 ID
  const conversationId = ref(null) // 对话 ID
  const sessions = ref([]) // 会话列表
  const activeToolCalls = ref([]) // 当前工具调用
  const currentPlan = ref(null) // ReWOO 规划
  const pendingHitl = ref(null) // HITL 确认
  const currentTrace = ref(null) // 追踪数据
  const pushMessages = ref([]) // 推送消息
  const turnStats = ref(null) // 统计
  const currentSessionTokens = ref({ total: 0, input: 0, output: 0 }) // 当前会话累计 Token
  const allSessionsTokens = computed(() =>
    sessions.value.reduce((a, s) => a + (s.tokenCount || 0), 0)
  )

  // 中断控制器
  let abortController = null

  const hasActiveToolCalls = computed(() => activeToolCalls.value.length > 0)

  // ===== 消息构造 =====
  function pushUserMessage(text) {
    messages.value.push({
      role: 'user',
      content: text,
      thinking: false,
      toolCalls: [],
      timestamp: Date.now()
    })
  }

  function pushAssistantPlaceholder() {
    const msg = {
      role: 'assistant',
      content: '',
      thinking: false,
      toolCalls: [],
      plan: null,
      trace: null,
      stats: null,
      tokens: null, // 本轮 token 消耗 {input, output, total, estimated}
      showTokens: false, // 是否展开 token 明细
      timestamp: Date.now()
    }
    messages.value.push(msg)
    return msg
  }

  // ===== 工具调用处理 =====
  function handleToolCall(data, assistantMsg) {
    const call = {
      id: data.id || data.callId || `call-${Date.now()}`,
      name: data.name || data.tool || '',
      args: data.args || data.arguments || {},
      result: null,
      status: 'running'
    }
    activeToolCalls.value.push(call)
    if (assistantMsg && Array.isArray(assistantMsg.toolCalls)) {
      assistantMsg.toolCalls.push(call)
    }
  }

  function handleToolResult(data, assistantMsg) {
    const callId = data.id || data.callId
    const call = activeToolCalls.value.find((c) => c.id === callId)
    if (call) {
      call.result = data.result || data.output
      call.status = 'done'
      // 完成后移出 active 列表
      activeToolCalls.value = activeToolCalls.value.filter((c) => c.id !== callId)
    }
    if (assistantMsg && Array.isArray(assistantMsg.toolCalls)) {
      const tc = assistantMsg.toolCalls.find((c) => c.id === callId)
      if (tc) {
        tc.result = data.result || data.output
        tc.status = 'done'
      }
    }
  }

  // ===== 事件状态机（§12.3） =====
  function handleEvent(eventType, data, assistantMsg) {
    switch (eventType) {
      case 'session':
        sessionId.value = data.sessionId
        if (data.conversationId) conversationId.value = data.conversationId
        break
      case 'thinking':
        assistantMsg.thinking = true
        break
      case 'text': {
        const delta = data.delta ?? data.text ?? ''
        assistantMsg.content += delta
        if (data.isLast) assistantMsg.thinking = false
        break
      }
      case 'tool_call':
        handleToolCall(data, assistantMsg)
        break
      case 'tool_result':
        handleToolResult(data, assistantMsg)
        break
      case 'plan':
        currentPlan.value = data.plan || data
        assistantMsg.plan = currentPlan.value
        break
      case 'hitl':
        pendingHitl.value = data
        break
      case 'trace':
        currentTrace.value = data
        assistantMsg.trace = data
        break
      case 'push':
        pushMessages.value.push(data)
        break
      case 'stats': {
        // 本轮 token 消耗 (后端每轮末发一次, 字段 totalTokens/inputTokens/outputTokens)
        const tTotal = data.totalTokens ?? data.tokens?.total ?? 0
        const tIn = data.inputTokens ?? data.tokens?.input ?? 0
        const tOut = data.outputTokens ?? data.tokens?.output ?? 0
        const estimated = data.estimated === true
        turnStats.value = data
        assistantMsg.stats = data
        // 写入本轮消息的 token 明细 (供点击查看)
        assistantMsg.tokens = { input: tIn, output: tOut, total: tTotal, estimated }
        // 累计到当前会话合计 (发送结束后会用 DB 值覆盖, 此处仅流式期即时反馈)
        currentSessionTokens.value = {
          total: (currentSessionTokens.value.total || 0) + tTotal,
          input: (currentSessionTokens.value.input || 0) + tIn,
          output: (currentSessionTokens.value.output || 0) + tOut
        }
        break
      }
      case 'error':
        assistantMsg.content += `\n[错误] ${data.error || data.message || ''}`
        assistantMsg.thinking = false
        message.error(data.error || data.message || 'Agent 处理出错')
        break
      case 'done':
        assistantMsg.thinking = false
        break
      case 'stop':
        // 服务端要求停止 / 用户停止通知
        sending.value = false
        assistantMsg.thinking = false
        if (abortController) {
          abortController.abort()
          abortController = null
        }
        break
      case 'data':
        // 通用数据帧：直接附加到当前消息内容（或更新 data 字段）
        if (data.delta !== undefined) {
          assistantMsg.content += data.delta
        } else if (data.text !== undefined) {
          assistantMsg.content += data.text
        }
        if (data.isLast) assistantMsg.thinking = false
        break
      default:
        // 未知事件类型忽略
        break
    }
  }

  // 解析单帧 SSE：取 event: 与 data:
  function parseFrame(frame) {
    const lines = frame.split('\n')
    let eventType = 'message'
    let dataStr = ''
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataStr += line.slice(5).trim()
      }
    }
    return { eventType, dataStr }
  }

  // ===== send(text) 方法（§12.3） =====
  async function send(text) {
    if (!text || !text.trim() || sending.value) return
    sending.value = true
    pushUserMessage(text.trim())
    const assistantMsg = pushAssistantPlaceholder()

    abortController = new AbortController()

    try {
      const response = await fetch('/api/agent/chat/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          satoken: authStore.token
        },
        body: JSON.stringify({
          message: text.trim(),
          sessionId: sessionId.value
        }),
        signal: abortController.signal
      })

      if (!response.ok) {
        const errText = await response.text().catch(() => '')
        throw new Error(`HTTP ${response.status}: ${errText}`)
      }
      if (!response.body) {
        throw new Error('响应体为空，不支持流式读取')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        // 解析 SSE 帧（event: type\ndata: json\n\n）
        const frames = buffer.split('\n\n')
        buffer = frames.pop() // 最后一个可能不完整

        for (const frame of frames) {
          if (!frame.trim()) continue
          const { eventType, dataStr } = parseFrame(frame)
          if (!dataStr) continue
          let data
          try {
            data = JSON.parse(dataStr)
          } catch (e) {
            // 非 JSON 数据，作为纯文本处理
            data = { delta: dataStr }
          }
          handleEvent(eventType, data, assistantMsg)
        }
      }
    } catch (err) {
      if (err.name === 'AbortError') {
        assistantMsg.content += '\n[已停止]'
      } else {
        assistantMsg.content += `\n[错误] ${err.message}`
        message.error(err.message || '流式请求失败')
      }
    } finally {
      assistantMsg.thinking = false
      sending.value = false
      abortController = null
      // 发送结束: 刷新侧边栏 (新会话/累计 token) 与当前会话合计 (DB 权威值)
      loadSessions()
      refreshSessionTokens(sessionId.value)
    }
  }

  // 停止当前请求
  function stop() {
    if (abortController) {
      abortController.abort()
    }
    sending.value = false
  }

  // HITL 确认/拒绝
  function resolveHitl(payload, confirmed) {
    pendingHitl.value = null
    // 用户确认后继续对话
    if (confirmed) {
      send(`[HITL 已确认] ${JSON.stringify(payload || {})}`)
    }
  }

  // 会话管理
  async function loadSessions() {
    try {
      const data = await listSessions()
      sessions.value = Array.isArray(data) ? data : data?.list || []
    } catch (e) {
      sessions.value = []
    }
  }

  async function newSession(title = '') {
    try {
      const data = await createSession(title)
      const s = data || { id: `session-${Date.now()}`, title: title || '新会话' }
      sessionId.value = s.id ?? s.sessionId
      sessions.value.unshift(s)
      messages.value = []
      currentSessionTokens.value = { total: 0, input: 0, output: 0 }
      return s
    } catch (e) {
      message.error('创建会话失败')
      return null
    }
  }

  async function selectSession(sid) {
    sessionId.value = sid
    pendingHitl.value = null
    currentPlan.value = null
    currentTrace.value = null
    turnStats.value = null
    // 加载该会话历史消息 + token 合计
    await loadSessionMessages(sid)
    refreshSessionTokens(sid)
  }

  // 加载会话历史消息, 映射为内部 msg 结构 (含历史 token 明细)
  async function loadSessionMessages(sid) {
    try {
      const list = await listSessionMessages(sid)
      const arr = Array.isArray(list) ? list : list?.list || []
      messages.value = arr.map((m) => ({
        role: m.role,
        content: m.content,
        thinking: false,
        toolCalls: [],
        tokens: m.tokens != null ? { input: 0, output: m.tokens, total: m.tokens } : null,
        showTokens: false,
        timestamp: null
      }))
    } catch (e) {
      messages.value = []
    }
  }

  // 从 DB 刷新当前会话 token 合计 (权威值, 覆盖流式期估算)
  async function refreshSessionTokens(sid) {
    if (sid == null || sid === 'default') return
    try {
      const stats = await getSessionTokens(sid)
      currentSessionTokens.value = {
        total: stats?.total_tokens ?? stats?.totalTokens ?? 0,
        input: stats?.input_tokens ?? stats?.inputTokens ?? 0,
        output: stats?.output_tokens ?? stats?.outputTokens ?? 0
      }
    } catch (e) {
      // 保留流式期累计值
    }
  }

  async function removeSession(sid) {
    try {
      await deleteSession(sid)
      sessions.value = sessions.value.filter((s) => s.sessionId !== sid)
      if (sessionId.value === sid) {
        sessionId.value = 'default'
        messages.value = []
      }
    } catch (e) {
      message.error('删除会话失败')
    }
  }

  // 清空当前对话
  function clearMessages() {
    messages.value = []
    pendingHitl.value = null
    currentPlan.value = null
    currentTrace.value = null
    turnStats.value = null
    currentSessionTokens.value = { total: 0, input: 0, output: 0 }
  }

  return {
    // state
    messages,
    sending,
    sessionId,
    conversationId,
    sessions,
    activeToolCalls,
    currentPlan,
    pendingHitl,
    currentTrace,
    pushMessages,
    turnStats,
    currentSessionTokens,
    allSessionsTokens,
    hasActiveToolCalls,
    // methods
    send,
    stop,
    resolveHitl,
    loadSessions,
    newSession,
    selectSession,
    removeSession,
    clearMessages,
    refreshSessionTokens,
    loadSessionMessages,
    handleEvent
  }
}

export default useAgentStream
