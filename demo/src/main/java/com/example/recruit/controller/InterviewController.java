package com.example.recruit.controller;

import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.mapper.InterviewMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 面试 API (复刻自文档 §14.7)。
 *
 * <p>GET  /api/interviews              面试列表
 * <p>POST /api/interviews              创建面试
 * <p>POST /api/interviews/{id}/questions 生成面试题
 * <p>GET  /api/interviews/{id}/questions 查看面试题
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewMapper interviewMapper;
    private final InterviewQuestionTool interviewQuestionTool;

    public InterviewController(InterviewMapper interviewMapper,
                               InterviewQuestionTool interviewQuestionTool) {
        this.interviewMapper = interviewMapper;
        this.interviewQuestionTool = interviewQuestionTool;
    }

    @GetMapping
    public List<Interview> list() {
        try {
            return interviewMapper.selectList(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    @PostMapping
    public Interview create(@RequestBody Interview body) {
        try {
            if (body.getStatus() == null) {
                body.setStatus("pending");
            }
            if (body.getCreatedAt() == null) {
                body.setCreatedAt(java.time.LocalDateTime.now());
            }
            interviewMapper.insert(body);
        } catch (Exception ignored) {
        }
        return body;
    }

    @PostMapping("/{id}/questions")
    public Map<String, Object> generateQuestions(@PathVariable Long id) {
        return interviewQuestionTool.generateQuestions(id);
    }

    @GetMapping("/{id}/questions")
    public List<Map<String, Object>> getQuestions(@PathVariable Long id) {
        return interviewQuestionTool.getQuestions(id);
    }
}
