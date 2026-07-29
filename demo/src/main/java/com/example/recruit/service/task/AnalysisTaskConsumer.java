package com.example.recruit.service.task;

import com.example.recruit.config.RabbitMQConfig;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.domain.analysis.ComparisonResult;
import com.example.recruit.domain.analysis.ResumeAnalysisResult;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.service.DocumentChunkService;
import com.example.recruit.service.ResumeAnalysisService;
import com.example.recruit.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 分析任务消费者: 真实执行 4+1 轮解析 + embedding + 语义分块, 以及简历对比。
 * 仅 real 模式 (mock=false) 启用。
 */
@Component
@ConditionalOnProperty(name = "app.mock.enabled", havingValue = "false")
public class AnalysisTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskConsumer.class);

    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeService resumeService;
    private final EmbeddingService embeddingService;
    private final TaskStatusManager taskStatusManager;
    private final DocumentChunkService documentChunkService;

    public AnalysisTaskConsumer(ResumeAnalysisService resumeAnalysisService,
                                 ResumeService resumeService,
                                 EmbeddingService embeddingService,
                                 TaskStatusManager taskStatusManager,
                                 DocumentChunkService documentChunkService) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.resumeService = resumeService;
        this.embeddingService = embeddingService;
        this.taskStatusManager = taskStatusManager;
        this.documentChunkService = documentChunkService;
    }

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_QUEUE)
    public void consume(AnalysisTaskMessage message) {
        log.info("Received analysis task: type={}, entityId={}", message.getTaskType(), message.getEntityId());
        taskStatusManager.updateStatus(message.getTaskId(), TaskStatus.Status.RUNNING, "分析中...");

        try {
            switch (message.getTaskType()) {
                case "RESUME_ANALYSIS" -> {
                    // 1. 4+1 轮深度解析
                    ResumeAnalysisResult result = resumeAnalysisService.analyzeFull(message.getEntityId());

                    // 2. 简历全文 embedding + 3. 语义分块
                    try {
                        Resume resume = resumeService.getById(message.getEntityId());
                        if (resume != null && resume.getRawText() != null) {
                            float[] embedding = embeddingService.embed(resume.getRawText());
                            resumeService.updateEmbedding(message.getEntityId(), embedding);
                            documentChunkService.chunkAndEmbedResume(resume);
                        }
                    } catch (Exception e) {
                        log.warn("Resume embedding/chunk failed: {}", e.getMessage());
                    }

                    taskStatusManager.setResult(message.getTaskId(), result);
                    taskStatusManager.updateStatus(message.getTaskId(), TaskStatus.Status.SUCCESS, "简历解析完成");
                }
                case "RESUME_COMPARE" -> {
                    ComparisonResult result = resumeAnalysisService.compareResumes(message.getResumeIds());
                    taskStatusManager.setResult(message.getTaskId(), result);
                    taskStatusManager.updateStatus(message.getTaskId(), TaskStatus.Status.SUCCESS, "对比分析完成");
                }
                default -> taskStatusManager.updateStatus(message.getTaskId(), TaskStatus.Status.FAILED,
                        "未知任务类型: " + message.getTaskType());
            }
        } catch (Exception e) {
            log.error("Analysis task failed: taskId={}, error={}", message.getTaskId(), e.getMessage(), e);
            taskStatusManager.updateStatus(message.getTaskId(), TaskStatus.Status.FAILED, e.getMessage());
        }
    }
}
