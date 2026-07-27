package com.example.recruit.controller;

import com.example.recruit.service.task.TaskStatusManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异步任务 API (复刻自文档 §14.11)。
 *
 * <p>GET /api/task/{taskId}/status 查询任务状态
 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskStatusManager taskStatusManager;

    public TaskController(TaskStatusManager taskStatusManager) {
        this.taskStatusManager = taskStatusManager;
    }

    @GetMapping("/{taskId}/status")
    public Map<String, Object> status(@PathVariable String taskId) {
        return taskStatusManager.getStatus(taskId);
    }
}
