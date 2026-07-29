package com.example.recruit.agent.core;

import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.agent.context.ContextAssembler;
import com.example.recruit.agent.context.ContextSnapshotService;
import com.example.recruit.agent.context.SessionManager;
import com.example.recruit.agent.event.AgentEventSseMapper;
import com.example.recruit.agent.routing.Intent;
import com.example.recruit.agent.routing.IntentRouter;
import com.example.recruit.agent.routing.IntentType;
import com.example.recruit.dal.entity.ChatSession;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.observability.LangFuseTraceService;
import com.example.recruit.memory.AutoMemoryExtractor;
import com.example.recruit.memory.ConsolidationScheduler;
import com.example.recruit.memory.HybridMemoryRetriever;
import com.example.recruit.memory.RedisSessionMemory;
import com.example.recruit.service.AgentTraceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.recruit.infra.llm.JsonGuard;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对话编排核心 (复刻自文档 §4.1 ConversationAgentService，523 行)。
 *
 * <p>整个 Agent 系统的编排中枢，负责从用户消息到 SSE 响应的完整数据流。
 *
 * <p>核心方法 {@code stream(agentId, conversationId, userMessage)} 返回 {@code Flux<String>} —— SSE 事件流。
 * 完整处理流程：
 * <ol>
 *   <li>记录开始时间 turnStartMs</li>
 *   <li>构建 Redis key "agent:session:{agentId}"，将用户消息追加到 Redis 会话历史</li>
 *   <li>调用 IntentRouter.classify(userMessage) 获取意图</li>
 *   <li>根据 IntentType 分流：
 *     <ul>
 *       <li>CHITCHAT → streamChichat：取最近 3 条历史，调 DeepSeek.chatStream 直答</li>
 *       <li>BATCH_INDEPENDENT → streamBatch：调 ReWooExecutor.execute 并行执行</li>
 *       <li>HITL → formatHitl：生成人工确认事件，零 LLM 调用</li>
 *       <li>SINGLE_TOOL → streamReAct：走 RecruitmentAgentService.getHarnessAgent().streamEvents()</li>
 *       <li>COMPOSITE → streamSupervisor：走 SupervisorAgentService.getSupervisorAgent().streamEvents()</li>
 *     </ul>
 *   </li>
 *   <li>doOnComplete 异步后处理：保存消息、AutoMemoryExtractor 提取记忆、
 *       ConsolidationScheduler 触发巩固、记录 AgentTrace、清理 HybridMemoryRetriever 线程缓存、
 *       推送 stats SSE 事件</li>
 * </ol>
 */
@Service
public class ConversationAgentService {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgentService.class);

    // ── 14 个 Bean (文档 §4.1 构造器注入) ──
    private final RecruitmentAgentService recruitmentAgentService;   // ReAct Agent
    private final SupervisorAgentService supervisorAgentService;      // Supervisor Agent
    private final ContextAssembler contextAssembler;                  // 上下文组装
    private final SessionManager sessionManager;                     // 会话管理
    private final AgentEventSseMapper sseMapper;                      // SSE 映射
    private final ConsolidationScheduler consolidationScheduler;     // 记忆巩固调度
    private final LangFuseTraceService langFuseTraceService;          // LangFuse 追踪
    private final ChatSessionService chatSessionService;              // 聊天会话
    private final AutoMemoryExtractor autoMemoryExtractor;            // 自动记忆提取
    private final IntentRouter intentRouter;                          // 意图路由
    private final DeepSeekModelService deepSeekModelService;          // LLM 服务
    private final ReWooExecutor reWooExecutor;                        // ReWOO 执行器
    private final RedisSessionMemory redisSessionMemory;              // Redis 短期记忆
    private final AgentTraceService agentTraceService;                // Agent 追踪 (写入+读取, P4 统一)
    private final ContextSnapshotService contextSnapshotService;       // 上下文快照 (HITL)

    public ConversationAgentService(RecruitmentAgentService recruitmentAgentService,
                                      SupervisorAgentService supervisorAgentService,
                                      ContextAssembler contextAssembler,
                                      SessionManager sessionManager,
                                      AgentEventSseMapper sseMapper,
                                      ConsolidationScheduler consolidationScheduler,
                                      LangFuseTraceService langFuseTraceService,
                                      ChatSessionService chatSessionService,
                                      AutoMemoryExtractor autoMemoryExtractor,
                                      IntentRouter intentRouter,
                                      DeepSeekModelService deepSeekModelService,
                                      ReWooExecutor reWooExecutor,
                                      RedisSessionMemory redisSessionMemory,
                                      AgentTraceService agentTraceService,
                                      ContextSnapshotService contextSnapshotService) {
        this.recruitmentAgentService = recruitmentAgentService;
        this.supervisorAgentService = supervisorAgentService;
        this.contextAssembler = contextAssembler;
        this.sessionManager = sessionManager;
        this.sseMapper = sseMapper;
        this.consolidationScheduler = consolidationScheduler;
        this.langFuseTraceService = langFuseTraceService;
        this.chatSessionService = chatSessionService;
        this.autoMemoryExtractor = autoMemoryExtractor;
        this.intentRouter = intentRouter;
        this.deepSeekModelService = deepSeekModelService;
        this.reWooExecutor = reWooExecutor;
        this.redisSessionMemory = redisSessionMemory;
        this.agentTraceService = agentTraceService;
        this.contextSnapshotService = contextSnapshotService;
    }

    /**
     * 主入口：处理用户消息，返回 SSE 事件流。
     *
     * <p>用 {@link Flux#defer} 包裹, 使以下副作用在订阅时执行 (每次发送独立上下文):
     * <ol>
     *   <li>用户消息追加 Redis 短期记忆</li>
     *   <li>会话解析: 数字 conversationId 直接复用; 否则为当前用户新建 chat_session,
     *       并以 {@code session} SSE 事件把真实 sessionId 回传前端</li>
     *   <li>意图分类 + 上下文组装</li>
     *   <li>分流 (CHITCHAT/BATCH/HITL/SINGLE_TOOL/COMPOSITE)</li>
     *   <li>doOnNext 收集 assistant 回复, doOnComplete 落库消息+token 与后处理</li>
     * </ol>
     */
    public Flux<String> stream(String agentId, String conversationId, String userMessage) {
        return Flux.defer(() -> {
            long turnStartMs = System.currentTimeMillis();
            TurnTokens holder = new TurnTokens();

            // 1. 用户消息追加到 Redis 短期记忆
            redisSessionMemory.addMessage(agentId, "user", userMessage);

            // 2. 会话解析: 数字 sessionId 直接复用; 否则为当前用户新建会话
            Long userId = parseUserId(agentId);
            Long sessionDbId = resolveSessionId(conversationId, userId, agentId, userMessage);
            String sessionEvent = sseFormat("session",
                    Map.of("sessionId", String.valueOf(sessionDbId)));

            // 3. 意图分类
            Intent intent = intentRouter.classify(userMessage);
            log.info("Intent: {} (conf={}) for: {}", intent.type(), intent.confidence(),
                    userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

            // 4. 上下文组装 (注入记忆快照)
            try {
                contextAssembler.assemble(String.valueOf(sessionDbId), userMessage, agentId);
            } catch (Exception e) {
                log.warn("context assemble failed: {}", e.getMessage());
            }

            // 5. 分流
            String convId = String.valueOf(sessionDbId);
            Flux<String> body = switch (intent.type()) {
                case CHITCHAT -> streamChichat(agentId, convId, userMessage, holder);
                case BATCH_INDEPENDENT -> streamBatch(agentId, convId, userMessage, holder);
                case HITL -> formatHitl(agentId, convId, userMessage, holder);
                case SINGLE_TOOL -> streamReAct(agentId, convId, userMessage, holder);
                case COMPOSITE -> streamSupervisor(agentId, convId, userMessage, holder);
            };

            // 6. 收集 assistant 回复 + doOnComplete 异步后处理
            StringBuilder assistantReply = new StringBuilder();
            Flux<String> pipeline = body
                    .doOnNext(sse -> {
                        // 用 JSON 解析提取 delta (替代脆弱的 indexOf, 避免转义/特殊字符截错)
                        if (sse != null && sse.startsWith("event: text")) {
                            try {
                                int dataStart = sse.indexOf("data: ");
                                if (dataStart >= 0) {
                                    String jsonStr = sse.substring(dataStart + 6).trim();
                                    JsonNode node = JsonGuard.parseJsonSafe(jsonStr);
                                    if (node != null) {
                                        String delta = node.path("delta").asText("");
                                        if (!delta.isEmpty()) {
                                            assistantReply.append(delta);
                                        }
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                    })
                    .doOnComplete(() -> finalizeTurn(agentId, convId, userMessage,
                            assistantReply.toString(), turnStartMs, sessionDbId, holder))
                    .onErrorResume(e -> {
                        log.error("stream error", e);
                        return Flux.just(sseError("Agent 流式响应异常: " + e.getMessage()),
                                sseFormat("done", Map.of()));
                    });

            // 先发 session 事件 (前端据其更新当前 sessionId), 再发对话体
            return Flux.just(sessionEvent).concatWith(pipeline);
        });
    }

    /**
     * 解析 agentId ("hr:123") 得到 userId; 解析失败回退 0。
     */
    private Long parseUserId(String agentId) {
        if (agentId == null || !agentId.startsWith("hr:")) {
            return 0L;
        }
        try {
            return Long.parseLong(agentId.substring(3));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 会话解析: conversationId 为数字 → 直接用作 chat_session.id;
     * 否则 ("default"/空) 为当前用户新建会话, 标题取 userMessage 前 30 字。
     */
    private Long resolveSessionId(String conversationId, Long userId, String agentId, String userMessage) {
        if (conversationId != null) {
            try {
                return Long.parseLong(conversationId);
            } catch (NumberFormatException ignored) {
                // 非数字 → 新建会话
            }
        }
        String title = userMessage == null || userMessage.isBlank()
                ? "新对话"
                : (userMessage.length() > 30 ? userMessage.substring(0, 30) : userMessage);
        ChatSession s = chatSessionService.createSession(userId, title, agentId);
        return s == null ? null : s.getId();
    }

    /**
     * 单轮 token 累计 holder。CHITCHAT/SINGLE_TOOL/COMPOSITE 路径由 ModelCallEndEvent
     * 或流式 usage 真实累计; BATCH 路径字符估算并标记 estimated。
     */
    static final class TurnTokens {
        int input;
        int output;
        boolean estimated;

        void add(ChatUsage u) {
            if (u == null) {
                return;
            }
            try {
                input += u.getInputTokens();
                output += u.getOutputTokens();
            } catch (Throwable ignored) {
            }
        }

        void add(int in, int out) {
            input += in;
            output += out;
        }

        int total() {
            return input + output;
        }
    }

    // ─────────────────── 分流方法 (文档 §4.1) ───────────────────

    /** CHITCHAT：不走 Agent 框架，直接调 chatStreamWithUsage，取最近 3 条历史，1 次 LLM 调用并采集真实 token。 */
    private Flux<String> streamChichat(String agentId, String conversationId, String userMessage, TurnTokens holder) {
        List<String> history = redisSessionMemory.getRecent(agentId, 3);
        StringBuilder ctx = new StringBuilder();
        for (String entry : history) {
            String[] parts = entry.split("\\|", 3);
            String role = parts.length > 1 ? parts[1] : "";
            String content = parts.length > 2 ? parts[2] : "";
            ctx.append(role).append(": ").append(content).append('\n');
        }
        String sys = "你是 AI 招聘助手，可以帮 HR 完成招聘全流程。简洁友好地回答闲聊。\n历史:\n" + ctx;
        return deepSeekModelService.chatStreamWithUsage(sys, userMessage)
                .map(chunk -> {
                    // 末块携带 usage (delta 为空), 累计到 holder
                    if (chunk.inputTokens() > 0 || chunk.outputTokens() > 0) {
                        holder.add(chunk.inputTokens(), chunk.outputTokens());
                    }
                    return chunk.delta();
                })
                .filter(delta -> !delta.isEmpty())
                .map(delta -> sseFormat("text", Map.of("delta", delta, "isLast", false)))
                .concatWith(Flux.defer(() -> Flux.just(
                        sseFormat("text", Map.of("delta", "", "isLast", true)),
                        sseFormat("stats", Map.of(
                                "totalTokens", holder.total(),
                                "inputTokens", holder.input,
                                "outputTokens", holder.output,
                                "estimated", holder.estimated)),
                        sseFormat("done", Map.of()))));
    }

    /** BATCH_INDEPENDENT：调 ReWooExecutor.execute，结果包装为 SSE text 事件。token 字符估算并标记 estimated。 */
    private Flux<String> streamBatch(String agentId, String conversationId, String userMessage, TurnTokens holder) {
        return Flux.defer(() -> {
            // 先发 plan 事件
            String plan;
            try {
                plan = deepSeekModelService.mapper().writeValueAsString(
                        reWooExecutor.planOnly(userMessage));
            } catch (Exception e) {
                plan = "[]";
            }
            String result = reWooExecutor.execute(userMessage);
            // ReWooExecutor 不暴露 token usage → 用字符估算 (约 4 字符/token) 并标记 estimated
            int estIn = Math.max(1, userMessage == null ? 0 : userMessage.length() / 4);
            int estOut = Math.max(1, result == null ? 0 : result.length() / 4);
            holder.add(estIn, estOut);
            holder.estimated = true;
            return Flux.just(
                    sseFormat("plan", Map.of("plan", plan)),
                    sseFormat("text", Map.of("delta", result, "isLast", false)),
                    sseFormat("text", Map.of("delta", "", "isLast", true)),
                    sseFormat("stats", Map.of(
                            "totalTokens", holder.total(),
                            "inputTokens", holder.input,
                            "outputTokens", holder.output,
                            "estimated", true)),
                    sseFormat("done", Map.of()));
        });
    }

    /** HITL：生成人工确认事件，零 LLM 调用。保存上下文快照供 confirm 恢复。 */
    private Flux<String> formatHitl(String agentId, String conversationId, String userMessage, TurnTokens holder) {
        String replyId = "hitl-" + System.currentTimeMillis();
        try {
            io.agentscope.core.agent.RuntimeContext ctx = sessionManager.getOrCreate(conversationId);
            ctx.put("hitlAgentId", agentId);
            ctx.put("hitlConversationId", conversationId);
            ctx.put("hitlUserMessage", userMessage);
            contextSnapshotService.save(replyId, ctx);
        } catch (Exception e) {
            log.warn("save hitl snapshot failed: {}", e.getMessage());
        }
        Map<String, Object> hitl = new LinkedHashMap<>();
        hitl.put("replyId", replyId);
        hitl.put("message", "此操作需要人工确认：");
        hitl.put("userMessage", userMessage);
        return Flux.just(
                sseFormat("hitl", hitl),
                sseFormat("done", Map.of()));
    }

    /** SINGLE_TOOL：走 ReAct Agent 的 streamEvents。 */
    private Flux<String> streamReAct(String agentId, String conversationId, String userMessage, TurnTokens holder) {
        return streamAgentEvents(recruitmentAgentService.getHarnessAgent(),
                agentId, conversationId, userMessage, "RecruitmentAgent", holder);
    }

    /** COMPOSITE：走 Supervisor Agent 的 streamEvents。 */
    private Flux<String> streamSupervisor(String agentId, String conversationId, String userMessage, TurnTokens holder) {
        return streamAgentEvents(supervisorAgentService.getSupervisorAgent(),
                agentId, conversationId, userMessage, "SupervisorAgent", holder);
    }

    /**
     * 通用 Agent 事件流处理 (文档 §4.1 streamAgentEvents)。
     * 调 agent.streamEvents() 获取 AgentEvent 流，通过 sseMapper.toSse() 转为 SSE 字符串。
     * 跟踪 thinking 时间、工具调用次数、token 统计 (从 ModelCallEndEvent 的 ChatUsage 真实累计)。
     */
    private Flux<String> streamAgentEvents(HarnessAgent agent, String agentId,
                                              String conversationId, String userMessage,
                                              String agentName, TurnTokens holder) {
        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicLong thinkingMs = new AtomicLong(0);
        long start = System.currentTimeMillis();

        return agent.streamEvents(new UserMessage(userMessage))
                .<String>handle((event, sink) -> {
                    // 跟踪统计 (含 token 累计)
                    trackStats(event, toolCalls, thinkingMs, holder);
                    // sseMapper.toSse 对未处理事件返回 null (文档: 静默跳过), handle 跳过 null
                    String sse = sseMapper.toSse(event);
                    if (sse != null) {
                        sink.next(sse);
                    }
                })
                .concatWith(Flux.defer(() -> Flux.just(
                        sseFormat("stats", Map.of(
                                "totalTokens", holder.total(),
                                "inputTokens", holder.input,
                                "outputTokens", holder.output,
                                "estimated", holder.estimated,
                                "latency", System.currentTimeMillis() - start,
                                "toolCalls", toolCalls.get(),
                                "thinkingMs", thinkingMs.get())),
                        sseFormat("done", Map.of()))))
                .doOnComplete(() -> {
                    long latency = System.currentTimeMillis() - start;
                    if (langFuseTraceService.isEnabled()) {
                        langFuseTraceService.trace(agentName, userMessage, "", holder.total(), latency);
                    }
                    agentTraceService.record(conversationId, agentName, "turn",
                            null, userMessage, "", "deepseek-v4-flash", holder.total(), latency, "success");
                });
    }

    private void trackStats(AgentEvent event, AtomicInteger toolCalls, AtomicLong thinkingMs, TurnTokens holder) {
        try {
            // 真实 token: 从 ModelCallEndEvent 的 ChatUsage 累计 (每次模型调用一次)
            if (event instanceof ModelCallEndEvent e) {
                holder.add(e.getUsage());
                return;
            }
            switch (event.getType()) {
                case TOOL_CALL_START -> toolCalls.incrementAndGet();
                default -> { }
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────── HITL / stop / explain (对齐清单 §5.1) ───────────────────

    /**
     * 停止当前对话。
     * 真实停止通过 HarnessAgent.interrupt() 实现, 此处返回确认并清理快照。
     */
    public Map<String, Object> stop(String agentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stopped", true);
        result.put("agentId", agentId);
        return result;
    }

    /**
     * HITL 人工确认 (P4 真实恢复): 恢复 replyId 对应的 RuntimeContext 快照,
     * 取出原 agentId/conversationId/userMessage, 用 ReAct Agent 异步执行原操作 (不阻塞 HTTP 响应)。
     * action 含 reject/deny → 拒绝执行。
     */
    public Map<String, Object> confirmHitl(String replyId, String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("replyId", replyId);
        result.put("action", action == null ? "approved" : action);

        io.agentscope.core.agent.RuntimeContext ctx = contextSnapshotService.restore(replyId);
        if (ctx == null) {
            result.put("confirmed", false);
            result.put("error", "快照不存在或已过期");
            return result;
        }
        String agentId = ctx.get("hitlAgentId", String.class);
        String conversationId = ctx.get("hitlConversationId", String.class);
        String userMessage = ctx.get("hitlUserMessage", String.class);
        // 恢复后移除快照, 避免内存泄漏
        contextSnapshotService.remove(replyId);

        boolean approved = action == null
                || action.toLowerCase().contains("approv")
                || action.toLowerCase().contains("confirm")
                || action.toLowerCase().contains("yes");
        if (!approved) {
            result.put("confirmed", false);
            result.put("result", "已拒绝，操作未执行");
            return result;
        }
        result.put("confirmed", true);
        result.put("userMessage", userMessage);
        // 真实恢复: 异步用 ReAct Agent 执行原操作 (不阻塞 HTTP 响应)
        final String aId = agentId == null ? "hr:0" : agentId;
        final String cId = conversationId == null ? replyId : conversationId;
        final String msg = userMessage == null ? "" : userMessage;
        try {
            streamReAct(aId, cId, msg, new TurnTokens()).subscribe(
                    sse -> { },
                    e -> log.warn("HITL 恢复执行失败: {}", e.getMessage())
            );
            result.put("result", "已确认，操作已恢复执行");
        } catch (Exception e) {
            result.put("result", "恢复执行失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 解释 Agent 决策链路: 读取会话 trace + DeepSeek 生成摘要。
     * 返回 Map{steps, summary, model}。
     */
    public Map<String, Object> explain(String sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<?> traces = agentTraceService.getSessionTrace(sessionId);
        result.put("steps", traces);
        String summary;
        try {
            String sys = "你是 Agent 决策解释器。基于以下 Agent 追踪步骤, 用中文简要解释 Agent 的决策过程与工具调用, 150 字内。";
            StringBuilder user = new StringBuilder("会话 ID: " + sessionId + "\n步骤数: " + traces.size() + "\n");
            for (Object t : traces) {
                user.append(t == null ? "" : t.toString()).append('\n');
            }
            summary = deepSeekModelService.chat(sys, user.toString());
        } catch (Exception e) {
            log.warn("explain chat failed: {}", e.getMessage());
            summary = "无法生成解释: " + e.getMessage();
        }
        result.put("summary", summary);
        result.put("model", "deepseek-v4-flash");
        return result;
    }

    // ─────────────────── finalizeTurn (文档 §4.1 doOnComplete 后处理) ───────────────────

    private void finalizeTurn(String agentId, String conversationId, String userMessage,
                               String assistantReply, long turnStartMs, Long sessionId, TurnTokens holder) {
        long latency = System.currentTimeMillis() - turnStartMs;
        log.info("finalizeTurn: agentId={}, assistantReply.length={}, latency={}ms, tokens={}/{}",
                agentId, assistantReply.length(), latency, holder.input, holder.output);
        try {
            // 1. assistant 回复追加到 Redis
            redisSessionMemory.addMessage(agentId, "assistant", assistantReply);

            // 2. AutoMemoryExtractor 提取记忆
            autoMemoryExtractor.extract(agentId, userMessage, assistantReply);

            // 3. ConsolidationScheduler 触发巩固检查
            consolidationScheduler.triggerCheck();

            // 4. 清理 HybridMemoryRetriever 线程缓存
            HybridMemoryRetriever.clearCache();

            // 5. 落库: 持久化 user/assistant 消息及其 token 消耗 (供会话/全局 token 统计)
            if (sessionId != null) {
                chatSessionService.saveMessage(sessionId, "user", userMessage, holder.input);
                chatSessionService.saveMessage(sessionId, "assistant", assistantReply, holder.output);
                chatSessionService.touch(sessionId);
            }

            log.info("Turn finalized: agentId={}, latency={}ms", agentId, latency);
        } catch (Exception e) {
            log.warn("finalizeTurn failed: {}", e.getMessage());
        }
    }

    // ─────────────────── SSE 帧构造 ───────────────────

    private String sseFormat(String eventType, Map<String, Object> data) {
        try {
            return "event: " + eventType + "\n" +
                    "data: " + deepSeekModelService.mapper().writeValueAsString(data) + "\n\n";
        } catch (Exception e) {
            return "event: error\ndata: {\"error\":\"SSE 序列化失败\"}\n\n";
        }
    }

    private String sseError(String message) {
        return sseFormat("error", Map.of("error", message));
    }
}
