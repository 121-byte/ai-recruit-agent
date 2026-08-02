import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/store/auth'
import {
  listSessions,
  createSession,
  deleteSession,
  updateSessionTitle,
  chatConfirm,
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
      reasoning: '', // DeepSeek 思维链 (reasoning_content) 逐 token 累积
      thinking: false, // 是否仍在思考阶段
      showReasoning: true, // 思考面板默认展开 (流式时实时可见)
      toolCalls: [],
      plan: null,
      trace: null,
      stats: null,
      tokens: null, // 本轮 token 消耗 {input, output, total, estimated}
      showTokens: false, // 是否展开 token 明细
      timestamp: Date.now()
    }
    messages.value.push(msg)
    // 从响应式数组取回代理对象；后续逐帧修改才能立即触发 Vue 渲染。
    return messages.value[messages.value.length - 1]
  }

  // ===== 工具调用处理 =====
  function handleToolCall(data, assistantMsg) {
    const callId = data.toolCallId || data.id || data.callId || `${data.name || data.tool || 'tool'}-${Date.now()}`
    let call = activeToolCalls.value.find((c) => c.id === callId)
    if (!call && assistantMsg && Array.isArray(assistantMsg.toolCalls)) {
      call = assistantMsg.toolCalls.find((c) => c.id === callId)
    }

    if (call) {
      call.name = data.name || data.tool || call.name
      if (data.args || data.arguments) {
        call.args = data.args || data.arguments
      }
      if (data.delta) {
        call.argsText = (call.argsText || '') + data.delta
        call.args = call.argsText
      }
      if (data.finished) {
        call.callFinished = true
      }
      return
    }

    const newCall = {
      id: callId,
      name: data.name || data.tool || '',
      args: data.args || data.arguments || {},
      result: null,
      status: 'running',
      callFinished: !!data.finished
    }
    if (data.delta) {
      newCall.argsText = data.delta
      newCall.args = data.delta
    }
    activeToolCalls.value.push(newCall)
    if (assistantMsg && Array.isArray(assistantMsg.toolCalls)) {
      assistantMsg.toolCalls.push(newCall)
    }
  }

  function handleToolResult(data, assistantMsg) {
    const callId = data.toolCallId || data.id || data.callId
    const findByIdOrName = (list) => {
      if (!Array.isArray(list)) return null
      return list.find((c) => callId && c.id === callId) ||
        [...list].reverse().find((c) => c.name && c.name === (data.name || data.tool))
    }
    const call = findByIdOrName(activeToolCalls.value)
    if (call) {
      const chunk = data.result ?? data.output
      if (chunk !== undefined && chunk !== null) {
        call.result = call.result == null ? chunk : `${call.result}${chunk}`
      }
      if (data.finished || data.state) {
        call.status = data.state && data.state !== 'SUCCESS' ? 'error' : 'done'
        activeToolCalls.value = activeToolCalls.value.filter((c) => c.id !== call.id)
      }
    }
    if (assistantMsg && Array.isArray(assistantMsg.toolCalls)) {
      const tc = findByIdOrName(assistantMsg.toolCalls)
      if (tc) {
        const chunk = data.result ?? data.output
        if (chunk !== undefined && chunk !== null) {
          tc.result = tc.result == null ? chunk : `${tc.result}${chunk}`
        }
        if (data.finished || data.state) {
          tc.status = data.state && data.state !== 'SUCCESS' ? 'error' : 'done'
        }
      }
    }
  }

  // ===== 事件状态机（§12.3） =====
  function normalizePlan(payload) {
    let raw = payload?.plan ?? payload?.steps ?? payload
    if (typeof raw === 'string') {
      try {
        raw = JSON.parse(raw)
      } catch (e) {
        raw = []
      }
    }
    if (raw && !Array.isArray(raw) && Array.isArray(raw.steps)) {
      raw = raw.steps
    }
    if (!Array.isArray(raw)) {
      raw = []
    }
    return raw.map((step, idx) => ({
      id: step.id || step.stepId || `step-${idx + 1}`,
      agent: step.agent || '',
      task: step.task || step.name || '',
      description: step.description || step.task || step.name || '',
      status: step.status || 'pending',
      ...step
    }))
  }

  function normalizeStepStatus(status) {
    const value = String(status || '').toLowerCase()
    if (['success', 'done', 'completed', 'complete'].includes(value)) return 'done'
    if (['failed', 'failure', 'error'].includes(value)) return 'error'
    if (['running', 'in_progress', 'processing'].includes(value)) return 'running'
    return value || 'pending'
  }

  function handleTaskUpdate(data, assistantMsg) {
    const plan = Array.isArray(assistantMsg.plan) ? assistantMsg.plan : []
    const stepId = data.id || data.stepId
    const idx = plan.findIndex((step) =>
      (stepId && step.id === stepId) ||
      (data.agent && data.task && step.agent === data.agent && step.task === data.task)
    )
    const patch = {
      id: stepId || `${data.agent || 'agent'}-${data.task || Date.now()}`,
      agent: data.agent || '',
      task: data.task || '',
      description: data.description || data.task || data.agent || '',
      status: normalizeStepStatus(data.status || data.state),
      error: data.error || ''
    }
    if (idx >= 0) {
      plan[idx] = { ...plan[idx], ...patch }
    } else {
      plan.push(patch)
    }
    assistantMsg.plan = [...plan]
    currentPlan.value = assistantMsg.plan
  }

  function handleEvent(eventType, data, assistantMsg) {
    switch (eventType) {
      case 'session':
        sessionId.value = data.sessionId
        if (data.conversationId) conversationId.value = data.conversationId
        break
      case 'thinking': {
        // 思维链: 逐 token 累积到 reasoning, 思考态由 active/isLast 控制
        const delta = data.delta ?? ''
        if (delta) assistantMsg.reasoning = (assistantMsg.reasoning || '') + delta
        if (data.active === false || data.isLast === true) {
          assistantMsg.thinking = false
        } else {
          assistantMsg.thinking = true
        }
        break
      }
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
        currentPlan.value = normalizePlan(data)
        assistantMsg.plan = currentPlan.value
        break
      case 'task_update':
        handleTaskUpdate(data, assistantMsg)
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
    const lines = frame.split(/\r?\n/)
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

  function consumeFrames(buffer, assistantMsg) {
    const frames = buffer.split(/\r?\n\r?\n/)
    const remainder = frames.pop()

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

    return remainder
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
        // SSE 允许使用 LF 或 CRLF 分隔帧；代理通常会保留 CRLF。
        buffer = consumeFrames(buffer, assistantMsg)
      }

      buffer += decoder.decode()
      if (buffer.trim()) {
        const { eventType, dataStr } = parseFrame(buffer)
        if (dataStr) {
          try {
            handleEvent(eventType, JSON.parse(dataStr), assistantMsg)
          } catch (e) {
            handleEvent(eventType, { delta: dataStr }, assistantMsg)
          }
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
  async function resolveHitl(payload, confirmed) {
    pendingHitl.value = null
    try {
      const result = await chatConfirm({ replyId: payload?.replyId, action: confirmed ? 'approved' : 'rejected' })
      if (!result?.confirmed && confirmed) {
        message.error(result?.error || '确认失败')
      }
      return result
    } catch (e) {
      message.error('确认请求失败')
      return null
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
      let pendingInputTokens = 0
      messages.value = arr.map((m) => {
        const msg = {
          role: m.role,
          content: m.content,
          reasoning: m.reasoning || '',
          thinking: false,
          showReasoning: false,
          toolCalls: [],
          tokens: null,
          showTokens: false,
          timestamp: null
        }
        if (m.role === 'user') {
          pendingInputTokens = m.tokens ?? 0
        } else if (m.role === 'assistant' && m.tokens != null) {
          msg.tokens = {
            input: pendingInputTokens,
            output: m.tokens,
            total: pendingInputTokens + m.tokens
          }
          pendingInputTokens = 0
        }
        return msg
      })
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
      const id = String(sid)
      sessions.value = sessions.value.filter((s) => String(s.id ?? s.sessionId) !== id)
      if (String(sessionId.value) === id) {
        sessionId.value = 'default'
        clearMessages()
      }
      return true
    } catch (e) {
      message.error('删除会话失败')
      return false
    }
  }

  async function renameSession(sid, title) {
    const trimmedTitle = title.trim()
    if (!trimmedTitle) return false
    try {
      await updateSessionTitle(sid, trimmedTitle)
      const session = sessions.value.find((s) => String(s.id ?? s.sessionId) === String(sid))
      if (session) session.title = trimmedTitle
      return true
    } catch (e) {
      message.error('会话重命名失败')
      return false
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
    renameSession,
    clearMessages,
    refreshSessionTokens,
    loadSessionMessages,
    handleEvent
  }
}

export default useAgentStream
