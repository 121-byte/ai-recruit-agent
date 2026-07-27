package com.example.recruit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.agent.core.ConversationAgentService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 对话控制器 (复刻自文档 §14.2 Agent 对话 API)。
 *
 * <p>核心接口 {@code POST /api/agent/chat/stream} —— SSE 流式对话。
 * 调用 {@link ConversationAgentService#stream} 返回 SSE 事件流。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ConversationAgentService conversationAgentService;
    private final ChatSessionService chatSessionService;

    public AgentController(ConversationAgentService conversationAgentService,
                            ChatSessionService chatSessionService) {
        this.conversationAgentService = conversationAgentService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * Agent SSE 流式对话 (核心接口)。
     * 请求体: { message, sessionId }
     * 响应: text/event-stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = body.containsKey("sessionId") ? String.valueOf(body.get("sessionId")) : "default";
        String agentId = currentAgentId();
        String conversationId = sessionId;
        // ConversationAgentService 输出已格式化的 SSE 帧字符串; 这里解析为 ServerSentEvent,
        // 由 Spring 的 SSE 编码器输出标准帧 (避免对 Flux<String> 自动 data: 包装导致的二次封装)
        return conversationAgentService.stream(agentId, conversationId, message)
                .map(AgentController::toSseEvent);
    }

    /**
     * 将 "event: X\ndata: Y\n\n" 帧字符串解析为 {@link ServerSentEvent}。
     * Spring 的 SSE 编码器会据其输出标准 SSE 帧。
     */
    static ServerSentEvent<String> toSseEvent(String frame) {
        if (frame == null || frame.isBlank()) {
            return ServerSentEvent.<String>builder().build();
        }
        String event = "message";
        StringBuilder data = new StringBuilder();
        for (String line : frame.split("\n")) {
            if (line.startsWith("event: ")) {
                event = line.substring("event: ".length()).trim();
            } else if (line.startsWith("data: ")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring("data: ".length()));
            }
        }
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data.toString())
                .build();
    }

    /**
     * Agent 非流式对话 —— 收集 SSE 流为最终文本。
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = body.containsKey("sessionId") ? String.valueOf(body.get("sessionId")) : "default";
        String agentId = currentAgentId();
        StringBuilder sb = new StringBuilder();
        conversationAgentService.stream(agentId, sessionId, message)
                .doOnNext(sse -> {
                    int idx = sse.indexOf("\"delta\":\"");
                    if (idx > 0) {
                        int end = sse.indexOf("\"", idx + 9);
                        if (end > idx + 9) {
                            sb.append(sse, idx + 9, end);
                        }
                    }
                })
                .blockLast();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", sb.toString());
        result.put("sessionId", sessionId);
        return result;
    }

    /** 停止当前对话 (复刻自文档 §14.2 /api/agent/stop)。 */
    @PostMapping("/stop")
    public Map<String, Object> stop() {
        // 真实停止通过 HarnessAgent.interrupt() 实现；此处返回确认
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stopped", true);
        return result;
    }

    /** HITL 人工确认 (复刻自文档 §14.2 /api/agent/hitl/confirm)。 */
    @PostMapping("/hitl/confirm")
    public Map<String, Object> hitlConfirm(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmed", true);
        result.put("replyId", body.get("replyId"));
        result.put("action", body.getOrDefault("action", "approved"));
        return result;
    }

    private String currentAgentId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "hr:0" : "hr:" + loginId;
    }
}
