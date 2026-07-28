package com.example.recruit.controller;

import com.example.recruit.service.task.TaskStatusManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异步任务状态 API (复刻自对齐清单 §5.10, @RequestMapping("/api/tasks"))。
 *
 * <p>GET /api/tasks/{taskId}/status 查询任务状态 (单复数对齐原项目)。
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
        return taskStatusManager.getStatus(taskId);
    }
}
