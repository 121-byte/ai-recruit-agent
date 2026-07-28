package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.InterviewReport;
import com.example.recruit.service.InterviewAgentService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 面试官工具 (复刻自文档 §8.6 InterviewAgentTool)。
 *
 * <p>薄封装：参数校验 + 调用 {@link InterviewAgentService} + 结果 truncate。
 * 业务逻辑与 Mapper/LLM 依赖均下沉到 Service。
 */
@Component
public class InterviewAgentTool {

    private static final int TRUNCATE = 400;

    private final InterviewAgentService interviewAgentService;

    public InterviewAgentTool(InterviewAgentService interviewAgentService) {
        this.interviewAgentService = interviewAgentService;
    }

    @Tool(
            name = "startInterview",
            description = "启动 AI 初面：生成开场白与第一题，建立面试会话。",
            concurrencySafe = false)
    public Map<String, Object> startInterview(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {
        Map<String, Object> result = interviewAgentService.startInitialInterview(interviewId);
        truncateField(result, "opening");
        return result;
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
        // Tool 仍以 interviewId 暴露给 Agent；此处需要会话 id。
        // 简化：取该面试最近的 session 进行评估。
        // 若无 session，则提示先调用 startInterview。
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return out;
        }
        // 直接复用 service 的 startInitialInterview 内部已建 session，
        // 这里通过一次启动幂等获取 session_id 后再评估。
        Map<String, Object> started = interviewAgentService.startInitialInterview(interviewId);
        Object sessionIdObj = started.get("session_id");
        if (sessionIdObj == null) {
            out.put("error", "未找到会话，请先调用 startInterview");
            out.put("interview_id", interviewId);
            return out;
        }
        Long sessionId = ((Number) sessionIdObj).longValue();
        Map<String, Object> evaluated = interviewAgentService.processAnswer(sessionId, answer, "medium");
        evaluated.put("interview_id", interviewId);
        truncateField(evaluated, "feedback");
        truncateField(evaluated, "next");
        return evaluated;
    }

    @Tool(
            name = "generateSummary",
            description = "生成面试总结报告（技术/沟通/解题/文化适配四维评分 + 优势/风险 + 录用建议）。",
            concurrencySafe = false)
    public Map<String, Object> generateSummary(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return out;
        }
        InterviewReport report = interviewAgentService.getReport(interviewId);
        if (report == null) {
            out.put("error", "生成报告失败");
            out.put("interview_id", interviewId);
            return out;
        }
        out.put("interview_id", interviewId);
        out.put("report_id", report.getId());
        out.put("overall_score", report.getOverallScore());
        out.put("hiring_suggestion", report.getHiringSuggestion());
        out.put("summary", truncate(report.getSummary()));
        return out;
    }

    private void truncateField(Map<String, Object> map, String field) {
        Object v = map.get(field);
        if (v instanceof String s) {
            map.put(field, truncate(s));
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > TRUNCATE ? s.substring(0, TRUNCATE) + "..." : s;
    }
}
