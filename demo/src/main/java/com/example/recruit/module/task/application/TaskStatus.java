package com.example.recruit.module.task.application;

import java.time.LocalDateTime;

/**
 * 任务状态 (携带 status/message/result/时间戳, 供前端轮询)。
 */
public class TaskStatus {

    public enum Status {
        PENDING, RUNNING, SUCCESS, FAILED
    }

    private String taskId;
    private String taskType;
    private Long entityId;
    private Status status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Object result;

    public TaskStatus(String taskId, String taskType, Long entityId) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.entityId = entityId;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
}
