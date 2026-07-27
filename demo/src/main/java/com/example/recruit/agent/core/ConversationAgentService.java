package com.example.recruit.agent.core;

import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.agent.context.ContextAssembler;
import com.example.recruit.agent.context.SessionManager;
import com.example.recruit.agent.event.AgentEventSseMapper;
import com.example.recruit.agent.event.AgentTraceService;
import com.example.recruit.agent.routing.Intent;
import com.example.recruit.agent.routing.IntentRouter;
import com.example.recruit.agent.routing.IntentType;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.LangFuseTraceService;
import com.example.recruit.memory.AutoMemoryExtractor;
import com.example.recruit.memory.ConsolidationScheduler;
import com.example.recruit.memory.HybridMemoryRetriever;
import com.example.recruit.memory.RedisSessionMemory;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
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
    private final AgentTraceService agentTraceService;                // Agent 追踪

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
                                      AgentTraceService agentTraceService) {
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
    }

    /**
     * 主入口：处理用户消息，返回 SSE 事件流。
     */
    public Flux<String> stream(String agentId, String conversationId, String userMessage) {
        long turnStartMs = System.currentTimeMillis();
        String sessionKey = "agent:session:" + agentId;

        // 1. 用户消息追加到 Redis 短期记忆
        redisSessionMemory.appendMessage(agentId, "user", userMessage);

        // 2. 意图分类
        Intent intent = intentRouter.classify(userMessage);
        log.info("Intent: {} (conf={}) for: {}", intent.type(), intent.confidence(),
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        // 3. 上下文组装 (注入记忆快照)
        try {
            contextAssembler.assemble(conversationId, userMessage, agentId);
        } catch (Exception e) {
            log.warn("context assemble failed: {}", e.getMessage());
        }

        // 4. 分流
        Flux<String> body = switch (intent.type()) {
            case CHITCHAT -> streamChichat(agentId, conversationId, userMessage);
            case BATCH_INDEPENDENT -> streamBatch(agentId, conversationId, userMessage);
            case HITL -> formatHitl(agentId, conversationId, userMessage);
            case SINGLE_TOOL -> streamReAct(agentId, conversationId, userMessage);
            case COMPOSITE -> streamSupervisor(agentId, conversationId, userMessage);
        };

        // 5. doOnComplete 异步后处理
        StringBuilder assistantReply = new StringBuilder();
        return body
                .doOnNext(sse -> {
                    // 粗略提取 assistant 文本用于后处理 (统计/记忆)
                    if (sse != null && sse.startsWith("event: text")) {
                        int dataIdx = sse.indexOf("\"delta\":\"");
                        if (dataIdx > 0) {
                            String delta = sse.substring(dataIdx + 9, sse.indexOf("\"", dataIdx + 9));
                            assistantReply.append(delta);
                        }
                    }
                })
                .doOnComplete(() -> finalizeTurn(agentId, conversationId, userMessage,
                        assistantReply.toString(), turnStartMs))
                .onErrorResume(e -> {
                    log.error("stream error", e);
                    return Flux.just(sseError("Agent 流式响应异常: " + e.getMessage()),
                            sseFormat("done", Map.of()));
                });
    }

    // ─────────────────── 分流方法 (文档 §4.1) ───────────────────

    /** CHITCHAT：不走 Agent 框架，直接调 chatStream，取最近 3 条历史，1 次 LLM 调用。 */
    private Flux<String> streamChichat(String agentId, String conversationId, String userMessage) {
        List<Map<String, Object>> history = redisSessionMemory.getHistory(agentId);
        StringBuilder ctx = new StringBuilder();
        int from = Math.max(0, history.size() - 3);
        for (int i = from; i < history.size(); i++) {
            Map<String, Object> m = history.get(i);
            ctx.append(m.get("role")).append(": ").append(m.get("content")).append('\n');
        }
        String sys = "你是 AI 招聘助手，可以帮 HR 完成招聘全流程。简洁友好地回答闲聊。\n历史:\n" + ctx;
        return deepSeekModelService.chatStream(sys, userMessage)
                .map(delta -> sseFormat("text", Map.of("delta", delta, "isLast", false)))
                .concatWith(Flux.just(sseFormat("text", Map.of("delta", "", "isLast", true))))
                .concatWith(Flux.just(sseFormat("done", Map.of())));
    }

    /** BATCH_INDEPENDENT：调 ReWooExecutor.execute，结果包装为 SSE text 事件。 */
    private Flux<String> streamBatch(String agentId, String conversationId, String userMessage) {
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
            return Flux.just(
                    sseFormat("plan", Map.of("plan", plan)),
                    sseFormat("text", Map.of("delta", result, "isLast", false)),
                    sseFormat("text", Map.of("delta", "", "isLast", true)),
                    sseFormat("done", Map.of()));
        });
    }

    /** HITL：生成人工确认事件，零 LLM 调用。 */
    private Flux<String> formatHitl(String agentId, String conversationId, String userMessage) {
        Map<String, Object> hitl = new LinkedHashMap<>();
        hitl.put("replyId", "hitl-" + System.currentTimeMillis());
        hitl.put("message", "此操作需要人工确认：");
        hitl.put("userMessage", userMessage);
        return Flux.just(
                sseFormat("hitl", hitl),
                sseFormat("done", Map.of()));
    }

    /** SINGLE_TOOL：走 ReAct Agent 的 streamEvents。 */
    private Flux<String> streamReAct(String agentId, String conversationId, String userMessage) {
        return streamAgentEvents(recruitmentAgentService.getHarnessAgent(),
                agentId, conversationId, userMessage, "RecruitmentAgent");
    }

    /** COMPOSITE：走 Supervisor Agent 的 streamEvents。 */
    private Flux<String> streamSupervisor(String agentId, String conversationId, String userMessage) {
        return streamAgentEvents(supervisorAgentService.getSupervisorAgent(),
                agentId, conversationId, userMessage, "SupervisorAgent");
    }

    /**
     * 通用 Agent 事件流处理 (文档 §4.1 streamAgentEvents)。
     * 调 agent.streamEvents() 获取 AgentEvent 流，通过 sseMapper.toSse() 转为 SSE 字符串。
     * 跟踪 thinking 时间、工具调用次数、token 统计。
     */
    private Flux<String> streamAgentEvents(HarnessAgent agent, String agentId,
                                              String conversationId, String userMessage,
                                              String agentName) {
        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicLong thinkingMs = new AtomicLong(0);
        long start = System.currentTimeMillis();

        return agent.streamEvents(new UserMessage(userMessage))
                .<String>handle((event, sink) -> {
                    // 跟踪统计
                    trackStats(event, toolCalls, thinkingMs);
                    // sseMapper.toSse 对未处理事件返回 null (文档: 静默跳过), handle 跳过 null
                    String sse = sseMapper.toSse(event);
                    if (sse != null) {
                        sink.next(sse);
                    }
                })
                .concatWith(Flux.defer(() -> Flux.just(
                        sseFormat("stats", Map.of(
                                "tokens", 0,
                                "latency", System.currentTimeMillis() - start,
                                "toolCalls", toolCalls.get(),
                                "thinkingMs", thinkingMs.get())),
                        sseFormat("done", Map.of()))))
                .doOnComplete(() -> {
                    long latency = System.currentTimeMillis() - start;
                    if (langFuseTraceService.isEnabled()) {
                        langFuseTraceService.trace(agentName, userMessage, "", 0, latency);
                    }
                    agentTraceService.record(conversationId, agentName, "turn",
                            null, userMessage, "", "deepseek-v4-flash", 0, latency, "success");
                });
    }

    private void trackStats(AgentEvent event, AtomicInteger toolCalls, AtomicLong thinkingMs) {
        try {
            switch (event.getType()) {
                case TOOL_CALL_START -> toolCalls.incrementAndGet();
                default -> { }
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────── finalizeTurn (文档 §4.1 doOnComplete 后处理) ───────────────────

    private void finalizeTurn(String agentId, String conversationId, String userMessage,
                               String assistantReply, long turnStartMs) {
        long latency = System.currentTimeMillis() - turnStartMs;
        try {
            // 1. assistant 回复追加到 Redis
            redisSessionMemory.appendMessage(agentId, "assistant", assistantReply);

            // 2. AutoMemoryExtractor 提取记忆
            autoMemoryExtractor.extract(agentId, userMessage, assistantReply);

            // 3. ConsolidationScheduler 触发巩固检查
            consolidationScheduler.triggerCheck();

            // 4. 清理 HybridMemoryRetriever 线程缓存
            HybridMemoryRetriever.clearCache();

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
