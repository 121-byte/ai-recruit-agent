package com.example.recruit.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.CandidateMatch;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.CandidateMatchMapper;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.example.recruit.llm.RerankService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人匹配工具 (复刻自文档 §8.4 CandidateMatchingTool + §6.5 四阶段匹配全链路)。
 *
 * <p>底层执行 {@code matchForJob(jobId)} 的四阶段匹配：
 * <ol>
 *   <li>Stage 1: pgvector Top20 召回 + 方向预过滤 (extractPositionFilters)</li>
 *   <li>Stage 2: 条件性 rerank 精排 (候选池 ≤10 才 rerank)</li>
 *   <li>Stage 3: LLM 三维评分 (skill/experience/soft)</li>
 *   <li>Stage 4: 透明加权排序 finalScore = skill*0.4 + exp*0.3 + soft*0.2 + vector*0.1</li>
 * </ol>
 * 匹配时自动创建 interview 记录。
 *
 * <p>实现说明：为兼容 H2/Mock 降级，向量召回改为 Java 端余弦相似度计算
 * (从 DB 取简历 batch + embedding，本地 cosine 排序)，逻辑与 pgvector 等价。
 */
@Component
public class CandidateMatchingTool {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchingTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final CandidateMatchMapper matchMapper;
    private final InterviewMapper interviewMapper;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final DeepSeekModelService deepSeekModelService;

    public CandidateMatchingTool(JobProfileMapper jobMapper,
                                   ResumeMapper resumeMapper,
                                   CandidateMatchMapper matchMapper,
                                   InterviewMapper interviewMapper,
                                   EmbeddingService embeddingService,
                                   RerankService rerankService,
                                   DeepSeekModelService deepSeekModelService) {
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.matchMapper = matchMapper;
        this.interviewMapper = interviewMapper;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.deepSeekModelService = deepSeekModelService;
    }

    @Tool(
            name = "matchCandidates",
            description = "为指定岗位匹配候选人，执行四阶段匹配（向量召回+方向过滤+条件rerank+LLM三维评分+透明加权），返回 Top5 候选人并自动创建面试记录。",
            concurrencySafe = false)
    public Map<String, Object> matchCandidates(
            @ToolParam(name = "jobId", description = "岗位 ID（先用 listJobs 查询）")
            Long jobId) {

        JobProfile job = jobMapper.selectById(jobId);
        if (job == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "岗位不存在: jobId=" + jobId);
            r.put("hint", "请先调用 listJobs 查询可用岗位 ID");
            return r;
        }

        // ── Stage 1: 向量召回 + 方向预过滤 ──
        float[] jobEmb = job.getEmbedding();
        if (jobEmb == null || jobEmb.length == 0) {
            jobEmb = embeddingService.embed(job.getTitle() + " " + (job.getJdText() == null ? "" : job.getJdText()));
        }
        List<String> filters = extractPositionFilters(job.getTitle());
        List<Resume> pool = recallResumes(jobEmb, filters, 20);

        // ── Stage 2: 条件性 rerank 精排 ──
        if (pool.size() > 0 && pool.size() <= 10) {
            List<String> docs = new ArrayList<>();
            for (Resume r : pool) {
                docs.add(buildCandidateSummary(r));
            }
            List<Integer> order = rerankService.rerank(job.getTitle(), docs, docs.size());
            List<Resume> reranked = new ArrayList<>();
            for (int idx : order) {
                if (idx < pool.size()) {
                    reranked.add(pool.get(idx));
                }
            }
            pool = reranked;
        }

        // ── Stage 3 + 4: LLM 三维评分 + 透明加权 ──
        List<MatchRow> rows = new ArrayList<>();
        int evalCount = Math.min(pool.size(), 5);
        for (int i = 0; i < evalCount; i++) {
            Resume r = pool.get(i);
            double vectorScore = FloatVectorTypeHandler.cosine(jobEmb, r.getEmbedding());
            Score3 s = llmScore(job, r);
            double finalScore = s.skill * 0.4 + s.experience * 0.3 + s.soft * 0.2 + vectorScore * 0.1;
            rows.add(new MatchRow(r, s, vectorScore, finalScore));
        }
        rows.sort(Comparator.comparingDouble((MatchRow m) -> -m.finalScore));

        // 持久化 + 自动创建 interview
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MatchRow row = rows.get(i);
            CandidateMatch cm = upsertMatch(job.getId(), row);
            Interview iv = createInterview(job.getId(), row.resume.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("resume_id", row.resume.getId());
            item.put("name", row.resume.getCandidateName());
            item.put("overall_score", round2(cm.getOverallScore()));
            item.put("skill_score", round2(cm.getSkillScore()));
            item.put("experience_score", round2(cm.getExperienceScore()));
            item.put("soft_score", round2(cm.getSoftScore()));
            item.put("vector_score", round2(cm.getVectorScore()));
            item.put("interview_id", iv.getId());
            item.put("summary", buildCandidateSummary(row.resume));
            result.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", job.getId());
        out.put("job_title", job.getTitle());
        out.put("candidate_count", rows.size());
        out.put("position_filters", filters);
        out.put("candidates", result);
        return out;
    }

    // ─── Stage 1: 召回 + 方向预过滤 ───

    private List<Resume> recallResumes(float[] jobEmb, List<String> filters, int topK) {
        try {
            List<Resume> all = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                    .orderByDesc(Resume::getCreatedAt));
            // 方向预过滤
            List<Resume> filtered = new ArrayList<>();
            for (Resume r : all) {
                if (matchFilters(r, filters)) {
                    filtered.add(r);
                }
            }
            if (filtered.isEmpty() && !filters.isEmpty()) {
                filtered = all;   // 回退无过滤
            }
            // 余弦相似度排序 (等价 pgvector <=>)
            filtered.sort((a, b) -> Double.compare(
                    FloatVectorTypeHandler.cosine(jobEmb, b.getEmbedding()),
                    FloatVectorTypeHandler.cosine(jobEmb, a.getEmbedding())));
            if (filtered.size() > topK) {
                filtered = filtered.subList(0, topK);
            }
            return filtered;
        } catch (Exception e) {
            log.warn("recallResumes failed: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean matchFilters(Resume r, List<String> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        String text = buildCandidateSummary(r) + " " + (r.getRawText() == null ? "" : r.getRawText());
        for (String f : filters) {
            if (text.contains(f)) {
                return true;
            }
        }
        return false;
    }

    /** 提取岗位方向关键词 (复刻自文档 §6.5 extractPositionFilters)。 */
    private List<String> extractPositionFilters(String jobTitle) {
        List<String> filters = new ArrayList<>();
        if (jobTitle == null) {
            return filters;
        }
        String lower = jobTitle.toLowerCase();
        if (lower.contains("java")) filters.add("Java");
        if (lower.contains("python")) filters.add("Python");
        if (lower.contains("前端") || lower.contains("frontend") || lower.contains("vue") || lower.contains("react"))
            filters.add("前端");
        if (lower.contains("后端") || lower.contains("backend")) filters.add("后端");
        if (lower.contains("全栈") || lower.contains("fullstack")) filters.add("全栈");
        if (lower.contains("算法") || lower.contains("ml") || lower.contains("ai")) filters.add("算法");
        if (lower.contains("运维") || lower.contains("devops")) filters.add("运维");
        if (lower.contains("测试") || lower.contains("qa")) filters.add("测试");
        if (lower.contains("产品") || lower.contains("pm")) filters.add("产品");
        return filters;
    }

    /** 候选人自然语言摘要 (复刻自文档 §6.5 buildCandidateSummary)。 */
    private String buildCandidateSummary(Resume r) {
        StringBuilder sb = new StringBuilder();
        JsonNode data = r.getParsedJson();
        if (data == null) {
            return r.getRawText() == null ? "" :
                    r.getRawText().substring(0, Math.min(120, r.getRawText().length()));
        }
        sb.append("候选人: ").append(data.path("name").asText(r.getCandidateName()));
        sb.append("，意向: ").append(data.path("intended_position").asText(""));
        sb.append("，经验: ").append(data.path("work_years").asText("")).append("年");
        JsonNode skills = data.path("skills");
        if (skills.isArray()) {
            sb.append("，技能: ");
            for (int i = 0; i < Math.min(skills.size(), 10); i++) {
                sb.append(skills.get(i).asText()).append(" ");
            }
        }
        JsonNode exps = data.path("work_experience");
        if (exps.isArray() && !exps.isEmpty()) {
            sb.append("，最近: ").append(exps.get(0).path("company").asText(""));
            sb.append("-").append(exps.get(0).path("position").asText(""));
        }
        return sb.toString();
    }

    // ─── Stage 3: LLM 三维评分 ───

    private Score3 llmScore(JobProfile job, Resume r) {
        String sys = """
                你是招聘评分器。对候选人与岗位的匹配度按三维打分 (0-100):
                - skill_score: 技能匹配度
                - experience_score: 经验匹配度
                - soft_score: 软素质评估
                以 JSON 输出: {"skill_score":80,"experience_score":75,"soft_score":70,"reason":"..."}""";
        String user = "岗位: " + job.getTitle() + "\nJD: " + job.getJdText()
                + "\n候选人: " + buildCandidateSummary(r);
        try {
            String reply = deepSeekModelService.chatJson(sys, user);
            JsonNode n = JsonGuard.parseJsonSafe(reply);
            if (n != null) {
                return new Score3(
                        n.path("skill_score").asDouble(50),
                        n.path("experience_score").asDouble(50),
                        n.path("soft_score").asDouble(50));
            }
        } catch (Exception e) {
            log.debug("llmScore failed: {}", e.getMessage());
        }
        // Mock/失败: 用向量相似度估算
        double base = 50 + 30 * FloatVectorTypeHandler.cosine(job.getEmbedding(), r.getEmbedding());
        return new Score3(base, base, base);
    }

    // ─── 持久化 ───

    private CandidateMatch upsertMatch(Long jobId, MatchRow row) {
        CandidateMatch cm = new CandidateMatch();
        cm.setJobId(jobId);
        cm.setResumeId(row.resume.getId());
        cm.setSkillScore(BigDecimal.valueOf(row.s.skill));
        cm.setExperienceScore(BigDecimal.valueOf(row.s.experience));
        cm.setSoftScore(BigDecimal.valueOf(row.s.soft));
        cm.setVectorScore(BigDecimal.valueOf(row.vectorScore));
        cm.setOverallScore(BigDecimal.valueOf(row.finalScore));
        ObjectNode details = MAPPER.createObjectNode();
        details.put("reason", "LLM 三维评分 + 透明加权");
        cm.setMatchDetails(details);
        cm.setCreatedAt(LocalDateTime.now());
        try {
            matchMapper.insert(cm);
        } catch (Exception e) {
            log.debug("insert match failed: {}", e.getMessage());
        }
        return cm;
    }

    private Interview createInterview(Long jobId, Long resumeId) {
        Interview iv = new Interview();
        iv.setJobId(jobId);
        iv.setResumeId(resumeId);
        iv.setRound(1);
        iv.setStatus("pending");
        iv.setCreatedAt(LocalDateTime.now());
        try {
            interviewMapper.insert(iv);
        } catch (Exception e) {
            log.debug("insert interview failed: {}", e.getMessage());
        }
        return iv;
    }

    private double round2(BigDecimal b) {
        if (b == null) return 0;
        return b.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private record Score3(double skill, double experience, double soft) {}

    private record MatchRow(Resume resume, Score3 s, double vectorScore, double finalScore) {}
}
