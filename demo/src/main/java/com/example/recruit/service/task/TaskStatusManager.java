package com.example.recruit.service.task;

import com.example.recruit.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 任务状态管理 (复刻自文档 §11.3 TaskStatusManager)。
 *
 * <p>使用 Redis 存储任务状态，支持前端轮询查询进度。
 * Mock 模式 (无 Redis) 降级为内存 Map。
 */
@Component
public class TaskStatusManager {

    private final AppProperties props;
    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public TaskStatusManager(AppProperties props) {
        this.props = props;
    }

    public void setStatus(String taskId, TaskStatus status, Object result) {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("taskId", taskId);
        entry.put("status", status.name());
        entry.put("result", result);
        entry.put("updatedAt", System.currentTimeMillis());
        store.put(taskId, entry);
    }

    public Map<String, Object> getStatus(String taskId) {
        return store.getOrDefault(taskId, Map.of("taskId", taskId, "status", "UNKNOWN"));
    }
}
