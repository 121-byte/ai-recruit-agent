package com.example.recruit.module.job.application;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岗位分析业务服务 (复刻对齐清单 §4.3)。
 *
 * <p>封装 LLM 解析 JD 为 weight_matrix / role_graph / growth_path / requirements，
 * 写回 job_profile + embedding。Tool 层不再持有 Mapper/LLM 依赖。
 */
@Service
public class JobAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobProfileMapper jobMapper;
    private final DeepSeekModelService deepSeek;
    private final EmbeddingService embeddingService;

    public JobAnalysisService(JobProfileMapper jobMapper,
                              DeepSeekModelService deepSeek,
                              EmbeddingService embeddingService) {
        this.jobMapper = jobMapper;
        this.deepSeek = deepSeek;
        this.embeddingService = embeddingService;
    }

    /**
     * 分析岗位 JD：LLM chatJson 解析为 weight_matrix / role_graph / growth_path / requirements，
     * 写回 job_profile + embedding，返回 Map。
     */
    public Map<String, Object> analyze(Long jobId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (jobId == null) {
            out.put("error", "jobId 不能为空");
            return out;
        }
        JobProfile job = jobMapper.selectById(jobId);
        if (job == null) {
            out.put("error", "岗位不存在: jobId=" + jobId);
            out.put("hint", "请先调用 listJobs 查询可用岗位 ID");
            return out;
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
            String reply = deepSeek.chatJson(sys, user);
            JsonNode parsed = JsonGuard.parseJsonSafe(reply);
            if (parsed == null || !parsed.isObject()) {
                parsed = MAPPER.createObjectNode();
            }

            job.setWeightMatrix(parsed.path("weight_matrix"));
            job.setRoleGraph(parsed.path("role_graph"));
            job.setGrowthPath(parsed.path("growth_path"));
            try {
                job.setEmbedding(embeddingService.embed(job.getJdText()));
            } catch (Throwable ignored) {
                log.debug("embed job jd failed");
            }
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);

            out.put("job_id", job.getId());
            out.put("title", job.getTitle());
            out.put("weight_matrix", parsed.path("weight_matrix"));
            out.put("role_graph", parsed.path("role_graph"));
            out.put("growth_path", parsed.path("growth_path"));
            out.put("requirements", parsed.path("requirements"));
            return out;
        } catch (Exception e) {
            log.warn("analyze job {} failed: {}", jobId, e.getMessage());
            out.put("error", "岗位分析失败: " + e.getMessage());
            return out;
        }
    }
}
