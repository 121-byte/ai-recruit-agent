package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.dal.entity.InterviewSession;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.InterviewReportMapper;
import com.example.recruit.dal.mapper.InterviewSessionMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 面试官工具 (复刻自文档 §8.6 InterviewAgentTool)。
 *
 * <p>返回值截断到 300-500 字，避免撑爆 ReAct 上下文。
 */
@Component
public class InterviewAgentTool {

    private static final Logger log = LoggerFactory.getLogger(InterviewAgentTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TRUNCATE = 400;

    private final InterviewMapper interviewMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final DeepSeekModelService deepSeekModelService;

    public InterviewAgentTool(InterviewMapper interviewMapper,
                               InterviewSessionMapper sessionMapper,
                               InterviewReportMapper reportMapper,
                               DeepSeekModelService deepSeekModelService) {
        this.interviewMapper = interviewMapper;
        this.sessionMapper = sessionMapper;
        this.reportMapper = reportMapper;
        this.deepSeekModelService = deepSeekModelService;
    }

    @Tool(
            name = "startInterview",
            description = "启动 AI 初面：生成开场白与第一题，建立面试会话。",
            concurrencySafe = false)
    public Map<String, Object> startInterview(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {

        Interview iv = interviewMapper.selectById(interviewId);
        if (iv == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "面试不存在: " + interviewId);
            return r;
        }
        String sys = "你是资深 AI 面试官。请用专业、友好的语气开场，并提出第一道面试题。";
        String reply = deepSeekModelService.chat(sys, "岗位相关面试，请开始。");

        InterviewSession session = new InterviewSession();
        session.setInterviewId(interviewId);
        ObjectNode messages = MAPPER.createObjectNode();
        ArrayNode arr = messages.putArray("messages");
        ObjectNode m1 = arr.addObject();
        m1.put("role", "interviewer");
        m1.put("content", reply);
        session.setMessages(messages);
        session.setCurrentRound(1);
        session.setDifficultyLevel("medium");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        try {
            sessionMapper.insert(session);
        } catch (Exception e) {
            log.debug("insert session failed: {}", e.getMessage());
        }

        iv.setStatus("scheduled");
        interviewMapper.updateById(iv);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interview_id", interviewId);
        out.put("session_id", session.getId());
        out.put("current_round", 1);
        out.put("difficulty", "medium");
        out.put("opening", truncate(reply));
        return out;
    }

    @Tool(
            name = "evaluateAnswer",
            description = "评估面试回答，给出追问或下一题。",
            concurrencySafe = false)
    public Map<String, Object> evaluateAnswer(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId,
            @ToolParam(name = "answer", description = "候选人的回答")
            String answer) {
        String sys = "你是 AI 面试官。请评估候选人回答，给出评分(0-100)、追问或下一题。以JSON输出: {\"score\":80,\"feedback\":\"...\",\"next\":\"追问/下一题内容\"}";
        String reply = deepSeekModelService.chatJson(sys, "回答: " + answer);
        Map<String, Object> out = new LinkedHashMap<>();
        JsonNode n = JsonGuard.parseJsonSafe(reply);
        if (n != null) {
            out.put("score", n.path("score").asInt(0));
            out.put("feedback", truncate(n.path("feedback").asText("")));
            out.put("next", truncate(n.path("next").asText("")));
        } else {
            out.put("feedback", truncate(reply));
        }
        out.put("interview_id", interviewId);
        return out;
    }

    @Tool(
            name = "generateSummary",
            description = "生成面试总结报告（技术/沟通/解题/文化适配四维评分 + 优势/风险 + 录用建议）。",
            concurrencySafe = false)
    public Map<String, Object> generateSummary(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {
        String sys = """
                你是面试评估专家。基于面试对话生成报告，以 JSON 输出:
                {"overall_score":0,"tech_score":0,"comm_score":0,"problem_solving_score":0,"culture_fit_score":0,
                 "strengths":["..."],"risks":["..."],"hiring_suggestion":"强烈推荐/推荐/待定/不推荐","summary":"..."}""";
        String reply = deepSeekModelService.chatJson(sys, "面试 ID: " + interviewId);
        JsonNode n = JsonGuard.parseJsonSafe(reply);
        InterviewReport report = new InterviewReport();
        report.setInterviewId(interviewId);
        if (n != null) {
            report.setOverallScore(bd(n.path("overall_score")));
            report.setTechScore(bd(n.path("tech_score")));
            report.setCommScore(bd(n.path("comm_score")));
            report.setProblemSolvingScore(bd(n.path("problem_solving_score")));
            report.setCultureFitScore(bd(n.path("culture_fit_score")));
            report.setHiringSuggestion(n.path("hiring_suggestion").asText(""));
            report.setSummary(n.path("summary").asText(""));
        }
        report.setCreatedAt(LocalDateTime.now());
        try {
            reportMapper.insert(report);
        } catch (Exception e) {
            log.debug("insert report failed: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("interview_id", interviewId);
        out.put("report_id", report.getId());
        out.put("overall_score", report.getOverallScore());
        out.put("hiring_suggestion", report.getHiringSuggestion());
        out.put("summary", truncate(report.getSummary()));
        return out;
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > TRUNCATE ? s.substring(0, TRUNCATE) + "..." : s;
    }

    private java.math.BigDecimal bd(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try {
            return java.math.BigDecimal.valueOf(n.asDouble());
        } catch (Exception e) {
            return null;
        }
    }
}
