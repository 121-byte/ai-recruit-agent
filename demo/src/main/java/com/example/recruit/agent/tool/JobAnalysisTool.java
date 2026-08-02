package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.module.job.application.JobAnalysisService;
import com.example.recruit.module.job.application.JobProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            description = "查询岗位列表，返回岗位 ID、标题、状态和部门。",
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
            description = "快速查看岗位详情和结构化岗位画像。优先读取数据库 parsed_json 缓存；没有缓存时只返回数据库基础岗位信息，不自动重新解析 JD。",
            concurrencySafe = false)
    public Map<String, Object> analyzeJob(
            @ToolParam(name = "jobId", description = "岗位 ID，先用 listJobs 查询")
            Long jobId) {
        if (jobId == null) {
            return errorMap("岗位 ID 不能为空");
        }

        JobProfile job = jobProfileService.getById(jobId);
        if (job == null) {
            Map<String, Object> out = errorMap("岗位不存在: " + jobId);
            out.put("hint", "请先调用 listJobs 获取可用岗位 ID");
            return out;
        }

        if (hasParsedJson(job.getParsedJson())) {
            return cachedJobDetail(job);
        }

        return basicJobDetail(job);
    }

    @Tool(
            name = "refreshJobAnalysis",
            description = "显式重新解析岗位 JD 并写回数据库。只有用户明确要求重新分析、重新解析或刷新岗位画像时才调用。",
            concurrencySafe = false)
    public Map<String, Object> refreshJobAnalysis(
            @ToolParam(name = "jobId", description = "岗位 ID，先用 listJobs 查询")
            Long jobId) {
        if (jobId == null) {
            return errorMap("岗位 ID 不能为空");
        }
        return jobAnalysisService.analyze(jobId);
    }

    private Map<String, Object> cachedJobDetail(JobProfile job) {
        JsonNode parsed = job.getParsedJson();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", job.getId());
        out.put("title", job.getTitle());
        out.put("status", job.getStatus());
        out.put("department", job.getDepartment());
        out.put("level", job.getLevel());
        out.put("location", job.getLocation());
        out.put("source", "cached_parsed_json");
        out.put("position_info", parsed.path("positionInfo"));
        out.put("skills", parsed.path("skills"));
        out.put("responsibilities", parsed.path("responsibilities"));
        out.put("project_context", parsed.path("projectContext"));
        out.put("education", parsed.path("education"));
        out.put("certifications", parsed.path("certifications"));
        out.put("requirements", parsed.path("requirements"));
        out.put("role_graph", parsed.path("roleGraph"));
        out.put("growth_path", parsed.path("growthPath"));
        return out;
    }

    private Map<String, Object> basicJobDetail(JobProfile job) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", job.getId());
        out.put("title", job.getTitle());
        out.put("status", job.getStatus());
        out.put("department", job.getDepartment());
        out.put("level", job.getLevel());
        out.put("location", job.getLocation());
        out.put("salary_min", job.getSalaryMin());
        out.put("salary_max", job.getSalaryMax());
        out.put("experience_min", job.getExperienceMin());
        out.put("experience_max", job.getExperienceMax());
        out.put("education", job.getEducation());
        out.put("headcount", job.getHeadcount());
        out.put("category", job.getCategory());
        out.put("jd_text", truncate(job.getJdText(), 1200));
        out.put("source", "database_basic");
        out.put("parsed_profile_available", false);
        out.put("hint", "如需重新生成结构化岗位画像，请调用 refreshJobAnalysis");
        return out;
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", message);
        return out;
    }

    private static boolean hasParsedJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isObject() || node.isArray()) {
            return node.size() > 0;
        }
        return !node.asText("").isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }
}
