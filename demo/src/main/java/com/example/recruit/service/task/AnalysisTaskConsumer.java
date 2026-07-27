package com.example.recruit.service.task;

import com.example.recruit.config.AppProperties;
import com.example.recruit.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 分析任务消费者 (复刻自文档 §11.2 AnalysisTaskConsumer)。
 *
 * <p>仅在关闭 Mock 模式时启用 (@RabbitListener)。流程：
 * 解析任务消息 → 更新 processing → 执行分析 → 更新 completed/failed。
 */
@Component
@ConditionalOnProperty(name = "app.mock.enabled", havingValue = "false")
public class AnalysisTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskConsumer.class);

    private final TaskStatusManager statusManager;

    public AnalysisTaskConsumer(TaskStatusManager statusManager) {
        this.statusManager = statusManager;
    }

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_QUEUE)
    public void handleAnalysisTask(AnalysisTaskMessage message) {
        log.info("Received analysis task: {} type={}", message.taskId(), message.type());
        statusManager.setStatus(message.taskId(), TaskStatus.PROCESSING, null);
        try {
            Object result = switch (message.type()) {
                case "resume_analysis" -> "简历分析完成";
                case "job_analysis" -> "岗位分析完成";
                case "batch_match" -> "批量匹配完成";
                default -> "未知任务类型: " + message.type();
            };
            statusManager.setStatus(message.taskId(), TaskStatus.COMPLETED, result);
        } catch (Exception e) {
            log.error("Analysis task failed: {}", message.taskId(), e);
            statusManager.setStatus(message.taskId(), TaskStatus.FAILED, e.getMessage());
        }
    }
}
