package com.example.recruit.controller;

import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.InterviewSession;
import com.example.recruit.dal.entity.Question;
import com.example.recruit.dal.mapper.InterviewSessionMapper;
import com.example.recruit.service.InterviewService;
import com.example.recruit.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试 API (复刻自对齐清单 §5.6, @RequestMapping("/api/interviews"))。
 *
 * <p>9 个权威端点:
 * <ul>
 *   <li>POST                                    创建面试</li>
 *   <li>PUT  /{id}/status                        更新面试状态</li>
 *   <li>GET  /{id}                               面试详情</li>
 *   <li>GET                                     面试列表</li>
 *   <li>GET  /job/{jobId}                        按岗位列出面试</li>
 *   <li>POST /{id}/questions/generate            生成面试题</li>
 *   <li>GET  /{id}/questions                     查看面试题</li>
 *   <li>PUT  /{interviewId}/questions/{questionId}/adopt  HR 采纳面试题</li>
 *   <li>GET  /{id}/stream                        获取面试会话(简化)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private static final int TRUNCATE = 400;

    private final InterviewService interviewService;
    private final QuestionService questionService;
    private final InterviewSessionMapper sessionMapper;

    public InterviewController(InterviewService interviewService,
                               QuestionService questionService,
                               InterviewSessionMapper sessionMapper) {
        this.interviewService = interviewService;
        this.questionService = questionService;
        this.sessionMapper = sessionMapper;
    }

    /** GET —— 面试列表。 */
    @GetMapping
    public List<Interview> list() {
        try {
            return interviewService.listAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** GET /job/{jobId} —— 按岗位列出面试。 */
    @GetMapping("/job/{jobId}")
    public List<Interview> listByJob(@PathVariable Long jobId) {
        try {
            return interviewService.listByJobId(jobId);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** GET /{id} —— 面试详情。 */
    @GetMapping("/{id}")
    public ResponseEntity<Interview> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(interviewService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    /** POST —— 创建面试。 */
    @PostMapping
    public Interview create(@RequestBody Interview body) {
        try {
            if (body.getStatus() == null) {
                body.setStatus("pending");
            }
            if (body.getCreatedAt() == null) {
                body.setCreatedAt(java.time.LocalDateTime.now());
            }
            interviewService.create(body);
        } catch (Exception ignored) {
        }
        return body;
    }

    /** PUT /{id}/status —— 更新面试状态。 */
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                            @RequestBody Map<String, String> body) {
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        boolean ok = interviewService.updateStatus(id, status);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("updated", ok);
        resp.put("status", status);
        return ResponseEntity.ok(resp);
    }

    /** POST /{id}/questions/generate —— 生成面试题 (原 /{id}/questions 改名)。 */
    @PostMapping("/{id}/questions/generate")
    public ResponseEntity<Map<String, Object>> generateQuestions(@PathVariable Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (id == null) {
            out.put("error", "interviewId 不能为空");
            return ResponseEntity.ok(out);
        }
        List<Question> questions;
        try {
            questions = questionService.generateQuestions(id);
        } catch (Exception e) {
            questions = List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_id", q.getId());
            item.put("type", q.getType());
            item.put("content", truncate(q.getContent()));
            item.put("follow_ups", q.getFollowUps());
            items.add(item);
        }
        out.put("interview_id", id);
        out.put("count", items.size());
        out.put("questions", items);
        return ResponseEntity.ok(out);
    }

    /** GET /{id}/questions —— 查看面试题。 */
    @GetMapping("/{id}/questions")
    public List<Map<String, Object>> getQuestions(@PathVariable Long id) {
        List<Question> list = interviewService.listQuestions(id);
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

    /** PUT /{interviewId}/questions/{questionId}/adopt —— HR 采纳面试题。 */
    @PutMapping("/{interviewId}/questions/{questionId}/adopt")
    public ResponseEntity<Map<String, Object>> adoptQuestion(@PathVariable Long interviewId,
                                                              @PathVariable Long questionId) {
        boolean ok = interviewService.adoptQuestion(questionId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("interview_id", interviewId);
        resp.put("question_id", questionId);
        resp.put("adopted", ok);
        return ResponseEntity.ok(resp);
    }

    /** GET /{id}/stream —— 获取面试会话 (简化: 返回该面试的会话)。 */
    @GetMapping("/{id}/stream")
    public ResponseEntity<InterviewSession> stream(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(sessionMapper.selectById(id));
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > TRUNCATE ? s.substring(0, TRUNCATE) + "..." : s;
    }
}
