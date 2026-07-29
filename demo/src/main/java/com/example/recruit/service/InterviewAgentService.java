package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.dal.entity.InterviewSession;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.InterviewReportMapper;
import com.example.recruit.dal.mapper.InterviewSessionMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 面试官业务服务 (复刻对齐清单 §4.2)。
 *
 * <p>封装面试会话开场、回答评估、流式评估、结束面试、辅助提示与面试报告生成等核心逻辑。
 * Tool 层不再持有 Mapper 或 LLM 调用，统一委托本服务。
 */
@Service
public class InterviewAgentService {

    private static final Logger log = LoggerFactory.getLogger(InterviewAgentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InterviewMapper interviewMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final DeepSeekModelService deepSeek;
    private final InterviewStatusBar statusBar;

    public InterviewAgentService(InterviewMapper interviewMapper,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewReportMapper reportMapper,
                                  DeepSeekModelService deepSeek,
                                  InterviewStatusBar statusBar) {
        this.interviewMapper = interviewMapper;
        this.sessionMapper = sessionMapper;
        this.reportMapper = reportMapper;
        this.deepSeek = deepSeek;
        this.statusBar = statusBar;
    }

    /**
     * 启动 AI 初面：取面试 → 创建/取会话 → LLM 生成开场白 + 第一题 → 写回消息。
     * interview.status 改为 scheduled。返回包含开场白的会话信息。
     */
    public Map<String, Object> startInitialInterview(Long interviewId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return out;
        }
        Interview iv = interviewMapper.selectById(interviewId);
        if (iv == null) {
            out.put("error", "面试不存在: " + interviewId);
            return out;
        }

        // 按 interviewId 查找或新建会话
        InterviewSession session = findSessionByInterviewId(interviewId);
        boolean isNew = session == null;
        if (isNew) {
            session = new InterviewSession();
            session.setInterviewId(interviewId);
            session.setMessages(initMessages());
            session.setCurrentRound(1);
            session.setDifficultyLevel("medium");
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            try {
                sessionMapper.insert(session);
            } catch (Exception e) {
                log.warn("insert session failed: {}", e.getMessage());
            }
        }

        // 生成开场白 + 第一题
        String sys = "你是资深 AI 面试官。请用专业、友好的语气开场，并提出第一道面试题。";
        String reply;
        try {
            reply = deepSeek.chat(sys, statusBar.appendTo("岗位相关面试，请开始。", session));
        } catch (Exception e) {
            log.warn("generate opening failed: {}", e.getMessage());
            reply = "[开场白生成失败] 请开始你的自我介绍。";
        }

        // 追加 interviewer 消息并写回
        ObjectNode messages = appendMessage(session.getMessages(), "interviewer", reply);
        session.setMessages(messages);
        session.setUpdatedAt(LocalDateTime.now());
        try {
            if (isNew) {
                sessionMapper.updateById(session);
            }
            sessionMapper.appendMessage(session.getId(), MAPPER.writeValueAsString(messages));
        } catch (Exception e) {
            log.warn("persist opening message failed: {}", e.getMessage());
        }

        // 更新面试状态
        iv.setStatus("scheduled");
        try {
            interviewMapper.updateById(iv);
        } catch (Exception e) {
            log.warn("update interview status failed: {}", e.getMessage());
        }

        out.put("interview_id", interviewId);
        out.put("session_id", session.getId());
        out.put("current_round", session.getCurrentRound());
        out.put("difficulty", session.getDifficultyLevel());
        out.put("opening", reply);
        return out;
    }

    /**
     * 评估候选人回答：LLM chatJson 输出 score/feedback/next，追加消息写回。
     * 返回 Map{score, feedback, next}。
     */
    public Map<String, Object> processAnswer(Long sessionId, String answer, String difficulty) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (sessionId == null) {
            out.put("error", "sessionId 不能为空");
            return out;
        }
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            out.put("error", "面试会话不存在: " + sessionId);
            return out;
        }

        // 追加候选人回答
        ObjectNode messages = appendMessage(session.getMessages(), "candidate", answer == null ? "" : answer);
        // 状态栏需反映「含最新回答」的对话，先回填再据此计算
        session.setMessages(messages);

        String sys = "你是 AI 面试官。请评估候选人回答，给出评分(0-100)、追问或下一题。以JSON输出: {\"score\":80,\"feedback\":\"...\",\"next\":\"追问/下一题内容\"}";
        if (difficulty != null && !difficulty.isBlank()) {
            sys += " 难度等级: " + difficulty;
        }
        String reply;
        try {
            reply = deepSeek.chatJson(sys, statusBar.appendTo("回答: " + (answer == null ? "" : answer), session));
        } catch (Exception e) {
            log.warn("evaluate answer failed: {}", e.getMessage());
            reply = "";
        }

        JsonNode node = JsonGuard.parseJsonSafe(reply);
        int score = 0;
        String feedback = "";
        String next = "";
        if (node != null) {
            score = node.path("score").asInt(0);
            feedback = node.path("feedback").asText("");
            next = node.path("next").asText("");
        } else {
            feedback = reply == null ? "" : reply;
        }

        // 追加评估/追问消息
        messages = appendMessage(messages, "interviewer", next == null || next.isEmpty() ? feedback : next);
        session.setMessages(messages);
        session.setUpdatedAt(LocalDateTime.now());
        try {
            sessionMapper.appendMessage(session.getId(), MAPPER.writeValueAsString(messages));
            sessionMapper.updateById(session);
        } catch (Exception e) {
            log.warn("persist evaluate message failed: {}", e.getMessage());
        }

        out.put("session_id", sessionId);
        out.put("score", score);
        out.put("feedback", feedback);
        out.put("next", next);
        return out;
    }

    /**
     * 流式评估候选人回答：用 DeepSeek.chatStream 返回评估+追问。
     * 每段包装为 ServerSentEvent.event("text").data(delta)。
     */
    public Flux<ServerSentEvent<String>> streamProcessAnswer(Long sessionId, String answer) {
        if (sessionId == null || answer == null) {
            return Flux.just(ServerSentEvent.<String>builder().event("text").data("[参数缺失]").build());
        }
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return Flux.just(ServerSentEvent.<String>builder().event("text").data("[会话不存在]").build());
        }
        String sys = "你是 AI 面试官。请评估候选人回答并给出追问，流式输出。";
        String user = statusBar.appendTo("回答: " + answer, session);
        return deepSeek.chatStream(sys, user)
                .map(delta -> ServerSentEvent.<String>builder().event("text").data(delta).build())
                .onErrorResume(e -> {
                    log.warn("stream evaluate failed: {}", e.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder().event("text").data("[流式评估失败]").build());
                });
    }

    /**
     * 结束面试：current_round +1 或标记结束，返回更新后的会话。
     */
    public InterviewSession endInterview(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        try {
            session.setCurrentRound((session.getCurrentRound() == null ? 0 : session.getCurrentRound()) + 1);
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        } catch (Exception e) {
            log.warn("endInterview failed: {}", e.getMessage());
        }
        return session;
    }

    /**
     * 为面试官生成提示建议（追问提示、关注点），返回 Map。
     */
    public Map<String, Object> getAssistSuggestion(Long interviewId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return out;
        }
        String sys = "你是面试官辅助助手。基于面试上下文给出提示建议，以 JSON 输出: {\"suggestion\":\"...\",\"focus_points\":[\"...\"]}";
        InterviewSession assistSession = findSessionByInterviewId(interviewId);
        String assistUser = statusBar.appendTo("面试 ID: " + interviewId, assistSession);
        String reply;
        try {
            reply = deepSeek.chatJson(sys, assistUser);
        } catch (Exception e) {
            log.warn("getAssistSuggestion failed: {}", e.getMessage());
            reply = "";
        }
        JsonNode node = JsonGuard.parseJsonSafe(reply);
        if (node != null) {
            out.put("suggestion", node.path("suggestion").asText(""));
            out.put("focus_points", node.path("focus_points"));
        } else {
            out.put("suggestion", reply);
        }
        out.put("interview_id", interviewId);
        return out;
    }

    /**
     * 生成面试报告：四维评分(tech/comm/problem_solving/culture_fit) + strengths[] + risks[]
     * + hiring_suggestion + summary，写入 InterviewReport 并返回 report。
     */
    public InterviewReport getReport(Long interviewId) {
        if (interviewId == null) {
            return null;
        }
        String sys = """
                你是面试评估专家。基于面试对话生成报告，以 JSON 输出:
                {"overall_score":0,"tech_score":0,"comm_score":0,"problem_solving_score":0,"culture_fit_score":0,
                 "strengths":["..."],"risks":["..."],"hiring_suggestion":"强烈推荐/推荐/待定/不推荐","summary":"..."}""";
        InterviewSession reportSession = findSessionByInterviewId(interviewId);
        String reportUser = statusBar.appendTo("面试 ID: " + interviewId, reportSession);
        String reply;
        try {
            reply = deepSeek.chatJson(sys, reportUser);
        } catch (Exception e) {
            log.warn("getReport chat failed: {}", e.getMessage());
            reply = "";
        }

        JsonNode node = JsonGuard.parseJsonSafe(reply);
        InterviewReport report = new InterviewReport();
        report.setInterviewId(interviewId);
        if (node != null) {
            report.setOverallScore(bd(node.path("overall_score")));
            report.setTechScore(bd(node.path("tech_score")));
            report.setCommScore(bd(node.path("comm_score")));
            report.setProblemSolvingScore(bd(node.path("problem_solving_score")));
            report.setCultureFitScore(bd(node.path("culture_fit_score")));
            report.setHiringSuggestion(node.path("hiring_suggestion").asText(""));
            report.setSummary(node.path("summary").asText(""));
            report.setStrengths(toStringArray(node.path("strengths")));
            report.setRisks(toStringArray(node.path("risks")));
        }
        report.setCreatedAt(LocalDateTime.now());
        try {
            reportMapper.insert(report);
        } catch (Exception e) {
            log.warn("insert report failed: {}", e.getMessage());
        }
        return report;
    }

    // ─────────────────── 内部工具 ───────────────────

    private InterviewSession findSessionByInterviewId(Long interviewId) {
        try {
            List<InterviewSession> list = sessionMapper.selectList(
                    new LambdaQueryWrapper<InterviewSession>()
                            .eq(InterviewSession::getInterviewId, interviewId)
                            .orderByDesc(InterviewSession::getId)
                            .last("LIMIT 1"));
            return list == null || list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("findSessionByInterviewId failed: {}", e.getMessage());
            return null;
        }
    }

    private ObjectNode initMessages() {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("messages");
        return root;
    }

    /** 在 messages JSONB 的 messages 数组末尾追加一条消息，返回新的 messages 对象。 */
    private ObjectNode appendMessage(JsonNode messages, String role, String content) {
        ObjectNode root;
        if (messages == null || messages.isMissingNode() || messages.isNull()) {
            root = initMessages();
        } else if (messages.isObject()) {
            try {
                root = (ObjectNode) messages;
            } catch (ClassCastException e) {
                root = initMessages();
            }
        } else {
            root = initMessages();
        }
        ArrayNode arr = root.has("messages") && root.get("messages").isArray()
                ? (ArrayNode) root.get("messages")
                : root.putArray("messages");
        ObjectNode item = arr.addObject();
        item.put("role", role == null ? "unknown" : role);
        item.put("content", content == null ? "" : content);
        item.put("timestamp", LocalDateTime.now().toString());
        return root;
    }

    private String[] toStringArray(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return new String[0];
        }
        List<String> list = new java.util.ArrayList<>();
        for (JsonNode n : node) {
            list.add(n.asText(""));
        }
        return list.toArray(new String[0]);
    }

    private BigDecimal bd(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        try {
            return BigDecimal.valueOf(n.asDouble());
        } catch (Exception e) {
            return null;
        }
    }
}
