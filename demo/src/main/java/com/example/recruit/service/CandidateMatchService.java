package com.example.recruit.service;

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
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.infra.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人匹配服务 (复刻自文档 §6.5 四阶段匹配全链路 + §8.4 Tool 拆出的业务逻辑)。
 *
 * <p>四阶段：
 * <ol>
 *   <li>pgvector Top20 召回 + 方向预过滤 (经 VectorSearchService 走原生 SQL)</li>
 *   <li>候选池 ≤10 时条件性 rerank 精排</li>
 *   <li>LLM 三维评分 (skill/experience/soft)</li>
 *   <li>透明加权 finalScore = skill*0.4 + exp*0.3 + soft*0.2 + vector*0.1</li>
 * </ol>
 * 匹配自动创建 interview 记录。Tool 层只做参数校验 + 调本 Service + truncate。
 */
@Service
public class CandidateMatchService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobProfileMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final CandidateMatchMapper matchMapper;
    private final InterviewMapper interviewMapper;
    private final VectorSearchService vectorSearchService;
    private final com.example.recruit.infra.retrieval.RerankService rerankService;
    private final DeepSeekModelService deepSeekModelService;
    private final EmbeddingService embeddingService;

    public CandidateMatchService(JobProfileMapper jobMapper,
                                   ResumeMapper resumeMapper,
                                   CandidateMatchMapper matchMapper,
                                   InterviewMapper interviewMapper,
                                   VectorSearchService vectorSearchService,
                                   com.example.recruit.infra.retrieval.RerankService rerankService,
                                   DeepSeekModelService deepSeekModelService,
                                   EmbeddingService embeddingService) {
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.matchMapper = matchMapper;
        this.interviewMapper = interviewMapper;
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
        this.deepSeekModelService = deepSeekModelService;
        this.embeddingService = embeddingService;
    }

    // ─────────────────── 四阶段匹配主流程 ───────────────────

    public Map<String, Object> matchForJob(Long jobId) {
        JobProfile job = jobMapper.selectById(jobId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (job == null) {
            out.put("error", "岗位不存在: jobId=" + jobId);
            return out;
        }

        // Stage 1: pgvector 召回 + 方向预过滤
        float[] jobEmb = job.getEmbedding();
        if (jobEmb == null || jobEmb.length == 0) {
            jobEmb = embeddingService.embed(job.getTitle() + " " + (job.getJdText() == null ? "" : job.getJdText()));
        }
        List<String> filters = extractPositionFilters(job.getTitle());
        List<Resume> pool = vectorSearchService.searchCandidates(jobEmb, 20, filters);

        // Stage 2: 条件性 rerank
        if (!pool.isEmpty() && pool.size() <= 10) {
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

        // Stage 3 + 4: LLM 三维评分 + 透明加权
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
            CandidateMatch cm = saveMatch(job.getId(), row);
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
            item.put("interview_id", iv == null ? null : iv.getId());
            item.put("summary", buildCandidateSummary(row.resume));
            result.add(item);
        }

        out.put("job_id", job.getId());
        out.put("job_title", job.getTitle());
        out.put("candidate_count", rows.size());
        out.put("position_filters", filters);
        out.put("candidates", result);
        return out;
    }

    // ─────────────────── CRUD ───────────────────

    public CandidateMatch create(CandidateMatch match) {
        if (match.getCreatedAt() == null) match.setCreatedAt(LocalDateTime.now());
        matchMapper.insert(match);
        return match;
    }

    public CandidateMatch update(CandidateMatch match) {
        matchMapper.updateById(match);
        return match;
    }

    public CandidateMatch getById(Long id) {
        return matchMapper.selectById(id);
    }

    public CandidateMatch getByJobAndResume(Long jobId, Long resumeId) {
        return matchMapper.selectOne(new LambdaQueryWrapper<CandidateMatch>()
                .eq(CandidateMatch::getJobId, jobId)
                .eq(CandidateMatch::getResumeId, resumeId));
    }

    public List<CandidateMatch> listByJobId(Long jobId) {
        return matchMapper.selectList(new LambdaQueryWrapper<CandidateMatch>()
                .eq(CandidateMatch::getJobId, jobId)
                .orderByDesc(CandidateMatch::getOverallScore));
    }

    public List<Map<String, Object>> listByJobIdWithResume(Long jobId) {
        try {
            return matchMapper.selectByJobIdWithResume(jobId);
        } catch (Exception e) {
            log.warn("listByJobIdWithResume failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<CandidateMatch> listSortedByJob(Long jobId) {
        return listByJobId(jobId);
    }

    /** 统计全部匹配记录数 (Dashboard 用)。 */
    public long count() {
        try {
            return matchMapper.selectCount(null);
        } catch (Exception e) {
            log.warn("count match failed: {}", e.getMessage());
            return 0L;
        }
    }

    public void feedback(Long id, String text) {
        CandidateMatch cm = matchMapper.selectById(id);
        if (cm != null) {
            cm.setHrFeedback(text);
            matchMapper.updateById(cm);
        }
    }

    // ─────────────────── 四阶段辅助 ───────────────────

    private List<String> extractPositionFilters(String jobTitle) {
        List<String> filters = new ArrayList<>();
        if (jobTitle == null) return filters;
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
        double base = 50 + 30 * FloatVectorTypeHandler.cosine(job.getEmbedding(), r.getEmbedding());
        return new Score3(base, base, base);
    }

    private CandidateMatch saveMatch(Long jobId, MatchRow row) {
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
        return b.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record Score3(double skill, double experience, double soft) {}

    private record MatchRow(Resume resume, Score3 s, double vectorScore, double finalScore) {}
}
