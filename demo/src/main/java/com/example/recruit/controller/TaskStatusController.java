package com.example.recruit.controller;

import com.example.recruit.service.task.TaskStatus;
import com.example.recruit.service.task.TaskStatusManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步任务状态 API (@RequestMapping("/api/tasks"))。
 *
 * <p>GET /api/tasks/{taskId}/status 查询任务状态。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskStatusController {

    private final TaskStatusManager taskStatusManager;

    public TaskStatusController(TaskStatusManager taskStatusManager) {
        this.taskStatusManager = taskStatusManager;
    }

    @GetMapping("/{taskId}/status")
    public Map<String, Object> status(@PathVariable String taskId) {
        TaskStatus ts = taskStatusManager.get(taskId);
        if (ts == null) {
            return Map.of("taskId", taskId, "status", "UNKNOWN");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", ts.getTaskId());
        m.put("status", ts.getStatus() == null ? "UNKNOWN" : ts.getStatus().name());
        m.put("message", ts.getMessage());
        m.put("result", ts.getResult());
        m.put("updatedAt", ts.getUpdatedAt());
        return m;
    }
}
