package com.example.recruit.service.task;

import java.util.Map;

/**
 * 分析任务消息 (复刻自文档 §11.2 AnalysisTaskMessage)。
 *
 * @param taskId 任务 ID
 * @param type   任务类型: resume_analysis / job_analysis / batch_match
 * @param params 任务参数
 * @param userId 提交用户 ID
 */
public record AnalysisTaskMessage(String taskId, String type, Map<String, Object> params, String userId) {}
