package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位分析工具 (复刻自文档 §8.3 JobAnalysisTool)。
 *
 * <p>提供 {@code listJobs} 查询岗位列表 与 {@code analyzeJob} LLM 解析 JD。
 * analyzeJob 内部调用 LLM 解析 JD 并生成结构化分析结果
 * (weight_matrix / role_graph / growth_path)，并写回 job_profile + 算 embedding。
 */
@Component
public class JobAnalysisTool {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobProfileMapper jobMapper;
    private final DeepSeekModelService deepSeekModelService;
    private final EmbeddingService embeddingService;

    public JobAnalysisTool(JobProfileMapper jobMapper,
                            DeepSeekModelService deepSeekModelService,
                            EmbeddingService embeddingService) {
        this.jobMapper = jobMapper;
        this.deepSeekModelService = deepSeekModelService;
        this.embeddingService = embeddingService;
    }

    @Tool(
            name = "listJobs",
            description = "查询岗位列表，返回所有岗位的 ID 和标题。",
            readOnly = true,
            concurrencySafe = true)
    public List<Map<String, Object>> listJobs() {
        try {
            List<JobProfile> jobs = jobMapper.selectList(null);
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

        Map<String, Object> error = Map.of();
        JobProfile job = jobMapper.selectById(jobId);
        if (job == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "岗位不存在: jobId=" + jobId);
            r.put("hint", "请先调用 listJobs 查询可用岗位 ID");
            return r;
        }

        String sys = """
                你是岗位分析专家。请从 JD 提取以下结构化信息：
                - weight_matrix: 技能权重矩阵 {技能: 0-1 权重}
                - role_graph: 角色图谱 {核心职责, 协作对象, 汇报关系}
                - growth_path: 成长路径 [阶段1, 阶段2, ...]
                - requirements: 学历/经验/技能硬性要求
                严格以 JSON 输出，不要 markdown 标记。""";
        String user = "岗位标题: " + job.getTitle() + "\nJD: " + job.getJdText();

        try {
            String reply = deepSeekModelService.chatJson(sys, user);
            JsonNode parsed = JsonGuard.parseJsonSafe(reply);
            if (parsed == null) {
                parsed = MAPPER.createObjectNode();
            }

            // 写回 job_profile
            job.setWeightMatrix(parsed.path("weight_matrix"));
            job.setRoleGraph(parsed.path("role_graph"));
            job.setGrowthPath(parsed.path("growth_path"));
            try {
                job.setEmbedding(embeddingService.embed(job.getJdText()));
            } catch (Throwable ignored) {
            }
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("job_id", job.getId());
            result.put("title", job.getTitle());
            result.put("weight_matrix", parsed.path("weight_matrix"));
            result.put("role_graph", parsed.path("role_graph"));
            result.put("growth_path", parsed.path("growth_path"));
            result.put("requirements", parsed.path("requirements"));
            return result;
        } catch (Exception e) {
            log.warn("analyzeJob failed: {}", e.getMessage());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "岗位分析失败: " + e.getMessage());
            return r;
        }
    }
}
