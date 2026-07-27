package com.example.recruit.controller;

import com.example.recruit.agent.tool.InterviewAgentTool;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 面试官 API (复刻自文档 §14.8)。
 *
 * <p>POST /api/interview-agent/{id}/start    启动 AI 初面
 * <p>POST /api/interview-agent/{id}/answer   提交面试回答
 * <p>POST /api/interview-agent/{id}/summary  生成面试总结
 */
@RestController
@RequestMapping("/api/interview-agent")
public class InterviewAgentController {

    private final InterviewAgentTool interviewAgentTool;

    public InterviewAgentController(InterviewAgentTool interviewAgentTool) {
        this.interviewAgentTool = interviewAgentTool;
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable Long id) {
        return interviewAgentTool.startInterview(id);
    }

    @PostMapping("/{id}/answer")
    public Map<String, Object> answer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String answer = body.get("answer") == null ? null : String.valueOf(body.get("answer"));
        return interviewAgentTool.evaluateAnswer(id, answer);
    }

    @PostMapping("/{id}/summary")
    public Map<String, Object> summary(@PathVariable Long id) {
        return interviewAgentTool.generateSummary(id);
    }
}
