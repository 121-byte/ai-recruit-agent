package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.module.job.application.JobAnalysisService;
import com.example.recruit.module.job.application.JobProfileService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位分析工具 (复刻自文档 §8.3 JobAnalysisTool)。
 *
 * <p>薄封装：
 * <ul>
 *   <li>{@code listJobs} 调用 {@link JobProfileService#listAll()}；</li>
 *   <li>{@code analyzeJob} 调用 {@link JobAnalysisService#analyze(Long)}。</li>
 * </ul>
 * Tool 不再注入 DeepSeek/Embedding，不写业务 SQL。
 */
@Component
public class JobAnalysisTool {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisTool.class);

    private final JobProfileService jobProfileService;
    private final JobAnalysisService jobAnalysisService;

    public JobAnalysisTool(JobProfileService jobProfileService,
                           JobAnalysisService jobAnalysisService) {
        this.jobProfileService = jobProfileService;
        this.jobAnalysisService = jobAnalysisService;
    }

    @Tool(
            name = "listJobs",
            description = "查询岗位列表，返回所有岗位的 ID 和标题。",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> listJobs() {
        try {
            List<JobProfile> jobs = jobProfileService.listAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (JobProfile j : jobs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("job_id", j.getId());
                item.put("title", j.getTitle());
                item.put("status", j.getStatus());
                item.put("department", j.getDepartment());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("listJobs failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Tool(
            name = "analyzeJob",
            description = "分析岗位 JD，提取技能要求、权重矩阵、角色图谱、成长路径。返回结构化分析结果。",
            concurrencySafe = false)
    public Map<String, Object> analyzeJob(
            @ToolParam(name = "jobId", description = "岗位 ID（先用 listJobs 查询）")
            Long jobId) {
        return jobAnalysisService.analyze(jobId);
    }
}
