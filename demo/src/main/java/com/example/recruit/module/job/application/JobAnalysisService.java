package com.example.recruit.module.job.application;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.infra.llm.JsonGuard;
import com.example.recruit.module.resume.application.DocumentChunkService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岗位分析业务服务。
 *
 * <p>封装 LLM 解析 JD 为镜像简历 structuredData 的结构化结果 (positionInfo/skills/responsibilities/
 * projectContext/education/certifications/requirements/roleGraph/growthPath)，写回 job_profile.parsed_json
 * + embedding，并触发岗位语义分块。Tool 层不再持有 Mapper/LLM 依赖。
 */
@Service
public class JobAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobProfileMapper jobMapper;
    private final DeepSeekModelService deepSeek;
    private final EmbeddingService embeddingService;
    private final DocumentChunkService documentChunkService;

    public JobAnalysisService(JobProfileMapper jobMapper,
                              DeepSeekModelService deepSeek,
                              EmbeddingService embeddingService,
                              DocumentChunkService documentChunkService) {
        this.jobMapper = jobMapper;
        this.deepSeek = deepSeek;
        this.embeddingService = embeddingService;
        this.documentChunkService = documentChunkService;
    }

    /**
     * 分析岗位 JD：LLM chatJson 解析为镜像简历 structuredData 的结构化结果，
     * 写回 job_profile.parsed_json + embedding，并触发岗位分块向量化，返回 Map。
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
                你是岗位分析专家。请从 JD 提取与简历结构化字段对齐的信息，便于后续逐项匹配。
                严格以 JSON 输出，不要 markdown 标记。字段如下：
                - positionInfo: { title, category, department, level, location,
                  experienceMin(最低年限,整数), experienceMax(最高年限,整数), education(学历要求),
                  salaryMin, salaryMax, headcount }
                - skills: [ { name, requiredLevel(熟练度1-5整数), weight(0-1权重,小数), years(要求年限,整数) } ]
                - responsibilities: [ { name, description, techStack:[] } ]
                - projectContext: [ { name, role, techStack:[], description } ]
                - education: { degree, major, school }
                - certifications: [ { name, prefer(true/false) } ]
                - requirements: { mustHaveSkills:[], niceToHaveSkills:[], softSkills:[] }
                - roleGraph: { coreDuties:[], collaborators:[], reporting }
                - growthPath: [ "阶段1", "阶段2", ... ]
                要求：skills 每项必须含 name 与 requiredLevel；无法提取的字段设为 null 或空数组，不要编造。""";
        String user = "岗位标题: " + job.getTitle() + "\nJD: " + job.getJdText();

        try {
            String reply = deepSeek.chatJson(sys, user);
            JsonNode parsed = JsonGuard.parseJsonSafe(reply);
            if (parsed == null || !parsed.isObject()) {
                parsed = MAPPER.createObjectNode();
            }

            job.setParsedJson(parsed);
            try {
                job.setEmbedding(embeddingService.embed(job.getJdText()));
            } catch (Throwable ignored) {
                log.debug("embed job jd failed");
            }
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);

            // 触发岗位语义分块 (与简历同 chunk_type, 供 chunk↔chunk 召回)
            try {
                documentChunkService.chunkAndEmbedJob(jobId);
            } catch (Exception e) {
                log.warn("chunkAndEmbedJob after analyze failed: {}", e.getMessage());
            }

            out.put("job_id", job.getId());
            out.put("title", job.getTitle());
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
        } catch (Exception e) {
            log.warn("analyze job {} failed: {}", jobId, e.getMessage());
            out.put("error", "岗位分析失败: " + e.getMessage());
            return out;
        }
    }
}
