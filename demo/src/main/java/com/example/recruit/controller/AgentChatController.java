package com.example.recruit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.agent.core.ConversationAgentService;
import com.example.recruit.agent.nudge.PreferenceLearningService;
import com.example.recruit.dal.entity.Question;
import com.example.recruit.service.AgentTraceService;
import com.example.recruit.service.CandidateMatchService;
import com.example.recruit.service.ExportService;
import com.example.recruit.service.InterviewService;
import com.example.recruit.service.JobAnalysisService;
import com.example.recruit.service.QuestionService;
import com.example.recruit.infra.llm.DeepSeekModelService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 对话控制器 (复刻自对齐清单 §5.1 AgentChatController, @RequestMapping("/api/agent"))。
 *
 * <p>10 个权威端点:
 * <ul>
 *   <li>POST /chat            非流式对话 (collect stream → 文本)</li>
 *   <li>POST /chat/stream     SSE 流式对话</li>
 *   <li>POST /chat/stop       停止对话</li>
 *   <li>POST /chat/confirm    HITL 人工确认 (非空返回)</li>
 *   <li>POST /chat/feedback   HR 反馈学习</li>
 *   <li>POST /chat/explain    解释 Agent 决策链路</li>
 *   <li>POST /jobs/{jobId}/analyze       岗位分析</li>
 *   <li>POST /jobs/{jobId}/match         候选人匹配</li>
 *   <li>POST /interviews/{interviewId}/questions 生成面试题</li>
 *   <li>POST /sessions/{sessionId}/export       导出会话</li>
 * </ul>
 *
 * <p>agentId 统一为 "hr:" + StpUtil.getLoginIdAsLong() (记忆隔离)。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final ConversationAgentService conversationAgentService;
    private final ChatSessionService chatSessionService;
    private final PreferenceLearningService preferenceLearningService;
    private final JobAnalysisService jobAnalysisService;
    private final CandidateMatchService candidateMatchService;
    private final QuestionService questionService;
    private final ExportService exportService;
    private final AgentTraceService agentTraceService;
    private final DeepSeekModelService deepSeekModelService;

    public AgentChatController(ConversationAgentService conversationAgentService,
                                ChatSessionService chatSessionService,
                                PreferenceLearningService preferenceLearningService,
                                JobAnalysisService jobAnalysisService,
                                CandidateMatchService candidateMatchService,
                                QuestionService questionService,
                                ExportService exportService,
                                AgentTraceService agentTraceService,
                                DeepSeekModelService deepSeekModelService) {
        this.conversationAgentService = conversationAgentService;
        this.chatSessionService = chatSessionService;
        this.preferenceLearningService = preferenceLearningService;
        this.jobAnalysisService = jobAnalysisService;
        this.candidateMatchService = candidateMatchService;
        this.questionService = questionService;
        this.exportService = exportService;
        this.agentTraceService = agentTraceService;
        this.deepSeekModelService = deepSeekModelService;
    }

    // ─────────────────── 对话端点 ───────────────────

    /** POST /chat/stream —— SSE 流式对话 (核心接口)。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = body.containsKey("sessionId") ? String.valueOf(body.get("sessionId")) : "default";
        String agentId = currentAgentId();
        String conversationId = sessionId;
        // ConversationAgentService 输出已格式化的 SSE 帧字符串; 这里解析为 ServerSentEvent,
        // 由 Spring 的 SSE 编码器输出标准帧 (避免对 Flux<String> 自动 data: 包装导致的二次封装)
        return conversationAgentService.stream(agentId, conversationId, message)
                .map(AgentChatController::toSseEvent);
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

    /** POST /chat —— 非流式对话, 收集 stream → 文本, 返回 {reply, sessionId}。 */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> body) {
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
        Map<String, String> result = new LinkedHashMap<>();
        result.put("reply", sb.toString());
        result.put("sessionId", sessionId);
        return ResponseEntity.ok(result);
    }

    /** POST /chat/stop —— 停止当前对话。 */
    @PostMapping("/chat/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestBody Map<String, String> body) {
        String agentId = currentAgentId();
        return ResponseEntity.ok(conversationAgentService.stop(agentId));
    }

    /** POST /chat/confirm —— HITL 人工确认 (非空返回)。 */
    @PostMapping("/chat/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestBody Map<String, Object> body) {
        String replyId = body.get("replyId") == null ? null : String.valueOf(body.get("replyId"));
        String action = body.get("action") == null ? "approved" : String.valueOf(body.get("action"));
        return ResponseEntity.ok(conversationAgentService.confirmHitl(replyId, action));
    }

    /** POST /chat/feedback —— HR 反馈学习。 */
    @PostMapping("/chat/feedback")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody Map<String, Object> body) {
        Long userId = currentUserId();
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        preferenceLearningService.processFeedback(userId, feedback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("userId", userId);
        return ResponseEntity.ok(result);
    }

    /** POST /chat/explain —— 解释 Agent 决策链路。 */
    @PostMapping("/chat/explain")
    public ResponseEntity<Map<String, Object>> explain(@RequestBody Map<String, Object> body) {
        String sessionId = body.get("sessionId") == null ? null : String.valueOf(body.get("sessionId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", agentTraceService.getSessionTrace(sessionId));
        String summary;
        try {
            String sys = "你是 Agent 决策解释器。基于会话追踪步骤, 用中文简要解释 Agent 的决策过程与工具调用, 150 字内。";
            summary = deepSeekModelService.chat(sys, "会话 ID: " + sessionId);
        } catch (Exception e) {
            summary = "无法生成解释: " + e.getMessage();
        }
        result.put("summary", summary);
        result.put("model", "deepseek-v4-flash");
        return ResponseEntity.ok(result);
    }

    // ─────────────────── 业务动作端点 ───────────────────

    /** POST /jobs/{jobId}/analyze —— 岗位分析。 */
    @PostMapping("/jobs/{jobId}/analyze")
    public ResponseEntity<Object> analyzeJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobAnalysisService.analyze(jobId));
    }

    /** POST /jobs/{jobId}/match —— 候选人匹配。 */
    @PostMapping("/jobs/{jobId}/match")
    public ResponseEntity<Object> matchCandidates(@PathVariable Long jobId) {
        return ResponseEntity.ok(candidateMatchService.matchForJob(jobId));
    }

    /** POST /interviews/{interviewId}/questions —— 生成面试题, 封装为 {interview_id,count,questions}。 */
    @PostMapping("/interviews/{interviewId}/questions")
    public ResponseEntity<Map<String, Object>> generateQuestions(@PathVariable Long interviewId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return ResponseEntity.ok(out);
        }
        List<Question> questions;
        try {
            questions = questionService.generateQuestions(interviewId);
        } catch (Exception e) {
            questions = List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_id", q.getId());
            item.put("type", q.getType());
            item.put("content", q.getContent());
            item.put("follow_ups", q.getFollowUps());
            items.add(item);
        }
        out.put("interview_id", interviewId);
        out.put("count", items.size());
        out.put("questions", items);
        return ResponseEntity.ok(out);
    }

    /** POST /sessions/{sessionId}/export —— 导出会话为文本。 */
    @PostMapping("/sessions/{sessionId}/export")
    public ResponseEntity<Map<String, String>> exportSession(@PathVariable String sessionId) {
        String content = exportService.exportSession(sessionId);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("content", content);
        return ResponseEntity.ok(result);
    }

    // ─────────────────── 工具 ───────────────────

    private String currentAgentId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "hr:0" : "hr:" + loginId;
    }

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? 0L : Long.parseLong(String.valueOf(loginId));
    }
}
