package com.example.recruit.service.task;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务状态管理 (内存 Map; 预留 Redis 降级)。
 */
@Component
public class TaskStatusManager {

    private final ConcurrentHashMap<String, TaskStatus> statusMap = new ConcurrentHashMap<>();

    public void put(TaskStatus status) {
        statusMap.put(status.getTaskId(), status);
    }

    public TaskStatus get(String taskId) {
        return statusMap.get(taskId);
    }

    public void updateStatus(String taskId, TaskStatus.Status status, String message) {
        TaskStatus ts = statusMap.get(taskId);
        if (ts != null) {
            ts.setStatus(status);
            ts.setMessage(message);
        }
    }

    public void setResult(String taskId, Object result) {
        TaskStatus ts = statusMap.get(taskId);
        if (ts != null) {
            ts.setResult(result);
        }
    }

    public void remove(String taskId) {
        statusMap.remove(taskId);
    }
}
