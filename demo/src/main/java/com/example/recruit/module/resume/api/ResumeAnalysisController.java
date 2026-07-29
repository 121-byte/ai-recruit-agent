package com.example.recruit.module.resume.api;

import com.example.recruit.config.AppProperties;
import com.example.recruit.config.RabbitMQConfig;
import com.example.recruit.module.resume.domain.ComparisonResult;
import com.example.recruit.module.resume.domain.ResumeAnalysisResult;
import com.example.recruit.module.resume.application.ResumeAnalysisService;
import com.example.recruit.module.task.application.AnalysisTaskMessage;
import com.example.recruit.module.task.application.TaskStatus;
import com.example.recruit.module.task.application.TaskStatusManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简历分析 API (@RequestMapping("/api/resumes"))。
 *
 * <p>2 个端点: POST /{resumeId}/analyze, POST /compare。
 * real 模式发 MQ 异步执行返回 taskId; mock 模式同步执行直接返回结果。
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final TaskStatusManager taskStatusManager;
    private final AppProperties appProperties;
    private final RabbitTemplate rabbitTemplate;

    public ResumeAnalysisController(ResumeAnalysisService resumeAnalysisService,
                                     TaskStatusManager taskStatusManager,
                                     AppProperties appProperties,
                                     @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.taskStatusManager = taskStatusManager;
        this.appProperties = appProperties;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** POST /{resumeId}/analyze —— 全量分析简历 (mock 同步 / real 异步返回 taskId)。 */
    @PostMapping("/{resumeId}/analyze")
    public ResponseEntity<?> analyze(@PathVariable Long resumeId) {
        if (appProperties.useMock() || rabbitTemplate == null) {
            // mock 模式或 MQ 未就绪: 同步执行
            ResumeAnalysisResult result = resumeAnalysisService.analyzeFull(resumeId);
            return ResponseEntity.ok(result);
        }
        AnalysisTaskMessage message = new AnalysisTaskMessage("RESUME_ANALYSIS", resumeId);
        TaskStatus taskStatus = new TaskStatus(message.getTaskId(), "RESUME_ANALYSIS", resumeId);
        taskStatusManager.put(taskStatus);
        rabbitTemplate.convertAndSend(RabbitMQConfig.ANALYSIS_EXCHANGE,
                RabbitMQConfig.ANALYSIS_ROUTING_KEY, message);
        return ResponseEntity.ok(Map.of(
                "taskId", message.getTaskId(),
                "status", "PENDING",
                "message", "简历解析任务已提交"));
    }

    /** POST /compare —— 横向对比多份简历 (body {resumeIds:[1,2,3]})。 */
    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestBody Map<String, Object> body) {
        List<Long> resumeIds = new ArrayList<>();
        Object ids = body.get("resumeIds");
        if (ids instanceof List<?> list) {
            for (Object o : list) {
                try {
                    resumeIds.add(Long.parseLong(String.valueOf(o)));
                } catch (Exception ignored) {
                }
            }
        }
        if (resumeIds.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "请至少选择2个简历进行对比"));
        }
        if (appProperties.useMock() || rabbitTemplate == null) {
            ComparisonResult result = resumeAnalysisService.compareResumes(resumeIds);
            return ResponseEntity.ok(result);
        }
        AnalysisTaskMessage message = new AnalysisTaskMessage("RESUME_COMPARE", resumeIds);
        TaskStatus taskStatus = new TaskStatus(message.getTaskId(), "RESUME_COMPARE", resumeIds.get(0));
        taskStatusManager.put(taskStatus);
        rabbitTemplate.convertAndSend(RabbitMQConfig.ANALYSIS_EXCHANGE,
                RabbitMQConfig.ANALYSIS_ROUTING_KEY, message);
        return ResponseEntity.ok(Map.of(
                "taskId", message.getTaskId(),
                "status", "PENDING",
                "message", "对比任务已提交"));
    }
}
