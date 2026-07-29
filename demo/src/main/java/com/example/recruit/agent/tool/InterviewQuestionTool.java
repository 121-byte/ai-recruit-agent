package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Question;
import com.example.recruit.module.interview.application.InterviewService;
import com.example.recruit.module.interview.application.QuestionService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试出题工具 (复刻自文档 §8.5 InterviewQuestionTool)。
 *
 * <p>薄封装：
 * <ul>
 *   <li>{@code generateQuestions} 调用 {@link QuestionService#generateQuestions(Long)}；</li>
 *   <li>{@code getQuestions} 调用 {@link InterviewService#listQuestions(Long)}。</li>
 * </ul>
 * Tool 不再注入 DeepSeek/Mapper。
 */
@Component
public class InterviewQuestionTool {

    private static final int TRUNCATE = 400;

    private final QuestionService questionService;
    private final InterviewService interviewService;

    public InterviewQuestionTool(QuestionService questionService,
                                  InterviewService interviewService) {
        this.questionService = questionService;
        this.interviewService = interviewService;
    }

    @Tool(
            name = "generateQuestions",
            description = "为面试生成面试题（技术/项目/行为三类），含追问。返回生成的题目列表。",
            concurrencySafe = false)
    public Map<String, Object> generateQuestions(
            @ToolParam(name = "interviewId", description = "面试 ID（来自 matchCandidates 输出）")
            Long interviewId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (interviewId == null) {
            out.put("error", "interviewId 不能为空");
            return out;
        }
        List<Question> questions = questionService.generateQuestions(interviewId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_id", q.getId());
            item.put("type", q.getType());
            item.put("content", truncate(q.getContent()));
            item.put("follow_ups", q.getFollowUps());
            items.add(item);
        }
        out.put("interview_id", interviewId);
        out.put("count", items.size());
        out.put("questions", items);
        return out;
    }

    @Tool(
            name = "getQuestions",
            description = "查看某场面试已有的面试题。",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> getQuestions(
            @ToolParam(name = "interviewId", description = "面试 ID")
            Long interviewId) {
        List<Question> list = interviewService.listQuestions(interviewId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Question q : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_id", q.getId());
            item.put("type", q.getType());
            item.put("content", truncate(q.getContent()));
            item.put("follow_ups", q.getFollowUps());
            item.put("hr_adopted", q.getHrAdopted());
            result.add(item);
        }
        return result;
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > TRUNCATE ? s.substring(0, TRUNCATE) + "..." : s;
    }
}
