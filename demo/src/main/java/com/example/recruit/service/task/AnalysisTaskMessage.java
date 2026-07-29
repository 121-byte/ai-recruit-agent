package com.example.recruit.service.task;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 分析任务消息 (MQ 序列化)。taskType: RESUME_ANALYSIS / RESUME_COMPARE。
 */
public class AnalysisTaskMessage implements Serializable {

    private String taskId;
    private String taskType;
    private Long entityId;
    private List<Long> resumeIds;  // 用于 RESUME_COMPARE

    public AnalysisTaskMessage() {
        this.taskId = UUID.randomUUID().toString();
    }

    public AnalysisTaskMessage(String taskType, Long entityId) {
        this.taskId = UUID.randomUUID().toString();
        this.taskType = taskType;
        this.entityId = entityId;
    }

    public AnalysisTaskMessage(String taskType, List<Long> resumeIds) {
        this.taskId = UUID.randomUUID().toString();
        this.taskType = taskType;
        this.resumeIds = resumeIds;
        this.entityId = resumeIds != null && !resumeIds.isEmpty() ? resumeIds.get(0) : null;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public List<Long> getResumeIds() { return resumeIds; }
    public void setResumeIds(List<Long> resumeIds) { this.resumeIds = resumeIds; }
}
