package com.example.recruit.module.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.CandidateMatch;
import com.example.recruit.dal.entity.Interview;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.CandidateMatchMapper;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.infra.llm.DeepSeekModelService;
import com.example.recruit.infra.llm.JsonGuard;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.infra.retrieval.RerankService;
import com.example.recruit.module.search.application.VectorSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Candidate matching service.
 *
 * <p>Hybrid Ranking v2 lite:
 * vector recall Top30 -> rerank Top10 -> evidence-based LLM assessment ->
 * score fusion -> decision tier -> persist result and conditionally create interview.
 */
@Service
public class CandidateMatchService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int VECTOR_RECALL_TOP = 30;
    private static final int RERANK_TOP = 10;
    private static final int DIRECT_EVAL_LIMIT = 5;
    private static final String VERSION = "hybrid-ranking-v2-lite";
    private static final MatchWeights DEFAULT_WEIGHTS = new MatchWeights(30, 25, 20, 10, 10, 5);

    private final JobProfileMapper jobMapper;
    private final CandidateMatchMapper matchMapper;
    private final InterviewMapper interviewMapper;
    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;
    private final DeepSeekModelService deepSeekModelService;
    private final EmbeddingService embeddingService;
    private final ExecutorService assessmentExecutor = Executors.newFixedThreadPool(4, new AssessmentThreadFactory());

    public CandidateMatchService(JobProfileMapper jobMapper,
                                 CandidateMatchMapper matchMapper,
                                 InterviewMapper interviewMapper,
                                 VectorSearchService vectorSearchService,
                                 RerankService rerankService,
                                 DeepSeekModelService deepSeekModelService,
                                 EmbeddingService embeddingService) {
        this.jobMapper = jobMapper;
        this.matchMapper = matchMapper;
        this.interviewMapper = interviewMapper;
        this.vectorSearchService = vectorSearchService;
        this.rerankService = rerankService;
        this.deepSeekModelService = deepSeekModelService;
        this.embeddingService = embeddingService;
    }

    public Map<String, Object> matchForJob(Long jobId) {
        return matchForJob(jobId, DEFAULT_WEIGHTS);
    }

    public Map<String, Object> matchForJob(Long jobId, MatchWeights weights) {
        MatchWeights resolvedWeights = MatchWeights.normalize(weights);
        JobProfile job = jobMapper.selectById(jobId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (job == null) {
            out.put("error", "岗位不存在 jobId=" + jobId);
            return out;
        }

        float[] jobEmbedding = ensureJobEmbedding(job);
        List<String> filters = extractPositionFilters(job);
        List<Resume> pool = vectorSearchService.searchCandidatesByJob(jobId, VECTOR_RECALL_TOP, filters);
        if (pool.isEmpty()) {
            pool = vectorSearchService.searchCandidates(jobEmbedding, VECTOR_RECALL_TOP, filters);
        }

        String rerankQuery = buildRerankQuery(job);
        List<RankedResume> rankedPool = rerankCandidates(pool, rerankQuery);

        List<MatchRow> rows = assessRankedPool(job, jobEmbedding, rerankQuery, rankedPool, resolvedWeights);
        rows.sort(Comparator.comparingDouble((MatchRow row) -> -row.finalScore()));
        rows = distinctRowsByResume(rows);

        replaceExistingMatches(job.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MatchRow row = rows.get(i);
            CandidateMatch cm = saveMatch(job, row);
            Interview iv = shouldCreateInterview(row.tier()) ? createInterview(job.getId(), row.ranked().resume().getId()) : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("resume_id", row.ranked().resume().getId());
            item.put("name", row.ranked().resume().getCandidateName());
            item.put("overall_score", round2(cm.getOverallScore()));
            item.put("skill_score", round2(cm.getSkillScore()));
            item.put("experience_score", round2(cm.getExperienceScore()));
            item.put("soft_score", round2(cm.getSoftScore()));
            item.put("vector_score", round2(cm.getVectorScore()));
            item.put("project_score", round2(row.assessment().projectScore()));
            item.put("rerank_score", round2(row.ranked().rerankScore()));
            item.put("decision_tier", row.tier());
            item.put("weight_config", row.weights().toPercentMap());
            item.put("interview_id", iv == null ? null : iv.getId());
            item.put("summary", row.assessment().summary());
            item.put("match_details", cm.getMatchDetails());
            result.add(item);
        }

        out.put("job_id", job.getId());
        out.put("job_title", job.getTitle());
        out.put("candidate_count", rows.size());
        out.put("recall_count", pool.size());
        out.put("position_filters", filters);
        out.put("rerank_query", rerankQuery);
        out.put("weights", resolvedWeights.toPercentMap());
        out.put("candidates", result);
        return out;
    }

    public CandidateMatch create(CandidateMatch match) {
        if (match.getCreatedAt() == null) {
            match.setCreatedAt(LocalDateTime.now());
        }
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
        List<CandidateMatch> matches = matchMapper.selectList(new LambdaQueryWrapper<CandidateMatch>()
                .eq(CandidateMatch::getJobId, jobId)
                .orderByDesc(CandidateMatch::getCreatedAt));
        return latestMatchPerResume(matches);
    }

    public List<Map<String, Object>> listByJobIdWithResume(Long jobId) {
        try {
            return latestMatchMapPerResume(matchMapper.selectByJobIdWithResume(jobId));
        } catch (Exception e) {
            log.warn("listByJobIdWithResume failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<CandidateMatch> listSortedByJob(Long jobId) {
        return listByJobId(jobId);
    }

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

    @PreDestroy
    public void shutdownAssessmentExecutor() {
        assessmentExecutor.shutdownNow();
    }

    private float[] ensureJobEmbedding(JobProfile job) {
        float[] jobEmbedding = job.getEmbedding();
        if (jobEmbedding == null || jobEmbedding.length == 0) {
            return embeddingService.embed(job.getTitle() + " " + (job.getJdText() == null ? "" : job.getJdText()));
        }
        return jobEmbedding;
    }

    private List<RankedResume> rerankCandidates(List<Resume> pool, String rerankQuery) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        if (pool.size() <= DIRECT_EVAL_LIMIT) {
            List<RankedResume> direct = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) {
                direct.add(new RankedResume(pool.get(i), i + 1, i + 1, rankScore(i), false));
            }
            return direct;
        }

        List<String> docs = pool.stream()
                .map(this::buildCandidateEvidenceDocument)
                .toList();
        List<RerankService.RerankResult> order = rerankService.rerankWithScore(
                rerankQuery, docs, Math.min(RERANK_TOP, docs.size()));

        List<RankedResume> reranked = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            int idx = order.get(i).index();
            if (idx >= 0 && idx < pool.size() && used.add(idx)) {
                reranked.add(new RankedResume(pool.get(idx), idx + 1, i + 1, order.get(i).score(), true));
            }
        }
        for (int i = 0; reranked.size() < Math.min(RERANK_TOP, pool.size()) && i < pool.size(); i++) {
            if (used.add(i)) {
                reranked.add(new RankedResume(pool.get(i), i + 1, reranked.size() + 1, rankScore(reranked.size()), false));
            }
        }
        return reranked;
    }

    private List<MatchRow> distinctRowsByResume(List<MatchRow> rows) {
        Map<Long, MatchRow> byResumeId = new LinkedHashMap<>();
        List<MatchRow> withoutResumeId = new ArrayList<>();
        for (MatchRow row : rows) {
            Long resumeId = row.ranked().resume().getId();
            if (resumeId == null) {
                withoutResumeId.add(row);
                continue;
            }
            byResumeId.putIfAbsent(resumeId, row);
        }
        List<MatchRow> result = new ArrayList<>(byResumeId.values());
        result.addAll(withoutResumeId);
        result.sort(Comparator.comparingDouble((MatchRow row) -> -row.finalScore()));
        return result;
    }

    private List<CandidateMatch> latestMatchPerResume(List<CandidateMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        Map<Long, CandidateMatch> byResumeId = new LinkedHashMap<>();
        List<CandidateMatch> withoutResumeId = new ArrayList<>();
        for (CandidateMatch match : matches) {
            Long resumeId = match.getResumeId();
            if (resumeId == null) {
                withoutResumeId.add(match);
                continue;
            }
            byResumeId.putIfAbsent(resumeId, match);
        }
        List<CandidateMatch> result = new ArrayList<>(byResumeId.values());
        result.addAll(withoutResumeId);
        result.sort(Comparator.comparing((CandidateMatch match) -> match.getOverallScore() == null
                ? BigDecimal.ZERO
                : match.getOverallScore()).reversed());
        return result;
    }

    private List<Map<String, Object>> latestMatchMapPerResume(List<Map<String, Object>> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<String, Object>> byResumeId = new LinkedHashMap<>();
        List<Map<String, Object>> withoutResumeId = new ArrayList<>();
        for (Map<String, Object> match : matches) {
            Object resumeIdValue = match.get("resume_id");
            if (resumeIdValue == null) {
                resumeIdValue = match.get("resumeId");
            }
            Long resumeId = asLong(resumeIdValue);
            if (resumeId == null) {
                withoutResumeId.add(match);
                continue;
            }
            byResumeId.putIfAbsent(resumeId, match);
        }
        List<Map<String, Object>> result = new ArrayList<>(byResumeId.values());
        result.addAll(withoutResumeId);
        result.sort(Comparator.comparingDouble((Map<String, Object> match) ->
                -asDouble(firstNonNull(match.get("overall_score"), match.get("overallScore")))));
        return result;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private void replaceExistingMatches(Long jobId) {
        try {
            matchMapper.deleteByJobId(jobId);
        } catch (Exception e) {
            log.warn("delete existing matches failed: jobId={}, error={}", jobId, e.getMessage());
        }
    }

    private List<MatchRow> assessRankedPool(JobProfile job,
                                            float[] jobEmbedding,
                                            String rerankQuery,
                                            List<RankedResume> rankedPool,
                                            MatchWeights weights) {
        if (rankedPool.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<MatchRow>> futures = new ArrayList<>();
        for (RankedResume ranked : rankedPool) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> assessOneCandidate(job, jobEmbedding, rerankQuery, ranked, weights),
                    assessmentExecutor));
        }

        List<MatchRow> rows = new ArrayList<>();
        for (CompletableFuture<MatchRow> future : futures) {
            rows.add(future.join());
        }
        return rows;
    }

    private MatchRow assessOneCandidate(JobProfile job,
                                        float[] jobEmbedding,
                                        String rerankQuery,
                                        RankedResume ranked,
                                        MatchWeights weights) {
        double vectorScore = normalizeScore(FloatVectorTypeHandler.cosine(jobEmbedding, ranked.resume().getEmbedding()));
        String evidenceText = buildCandidateEvidenceDocument(ranked.resume());
        MatchAssessment assessment = llmAssess(job, ranked.resume(), rerankQuery, evidenceText, vectorScore, ranked);
        double finalScore = weights.weightedScore(assessment, vectorScore, ranked.rerankScore());
        String tier = decisionTier(finalScore, assessment);
        return new MatchRow(ranked, assessment, vectorScore, finalScore, tier, weights);
    }

    private List<String> extractPositionFilters(JobProfile job) {
        List<String> filters = extractPositionFilters(job.getTitle());
        JsonNode parsed = job.getParsedJson();
        addFilterIfPresent(filters, parsed, "category");
        JsonNode info = parsed == null ? null : parsed.path("positionInfo");
        addFilterIfPresent(filters, info, "category");
        return filters.stream().distinct().toList();
    }

    private void addFilterIfPresent(List<String> filters, JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String value = node.path(fieldName).asText("");
        if (!value.isBlank()) {
            filters.add(value);
        }
    }

    private List<String> extractPositionFilters(String jobTitle) {
        List<String> filters = new ArrayList<>();
        if (jobTitle == null) {
            return filters;
        }
        String lower = jobTitle.toLowerCase();
        if (lower.contains("java")) filters.add("Java");
        if (lower.contains("python")) filters.add("Python");
        if (lower.contains("前端") || lower.contains("frontend") || lower.contains("vue") || lower.contains("react")) filters.add("前端");
        if (lower.contains("后端") || lower.contains("backend")) filters.add("后端");
        if (lower.contains("全栈") || lower.contains("fullstack")) filters.add("全栈");
        if (lower.contains("算法") || lower.contains("ml") || lower.contains("ai")) filters.add("算法");
        if (lower.contains("运维") || lower.contains("devops")) filters.add("运维");
        if (lower.contains("测试") || lower.contains("qa")) filters.add("测试");
        if (lower.contains("产品") || lower.contains("pm")) filters.add("产品");
        return filters;
    }

    private String buildRerankQuery(JobProfile job) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "岗位", job.getTitle());
        appendField(sb, "部门", job.getDepartment());
        appendField(sb, "职级", job.getLevel());
        appendField(sb, "地点", job.getLocation());
        if (job.getExperienceMin() != null || job.getExperienceMax() != null) {
            appendField(sb, "年限要求", (job.getExperienceMin() == null ? "" : job.getExperienceMin())
                    + "-" + (job.getExperienceMax() == null ? "" : job.getExperienceMax()) + "年");
        }
        appendField(sb, "学历要求", job.getEducation());

        JsonNode parsed = job.getParsedJson();
        if (parsed != null && !parsed.isNull()) {
            JsonNode info = parsed.path("positionInfo");
            appendField(sb, "岗位类别", textOf(info, "category"));
            appendField(sb, "核心技能", jsonToCompactText(parsed.path("skills"), 600));
            appendField(sb, "岗位职责", jsonToCompactText(parsed.path("responsibilities"), 600));
            appendField(sb, "项目背景", jsonToCompactText(parsed.path("projectContext"), 400));
            appendField(sb, "要求", jsonToCompactText(parsed.path("requirements"), 400));
        }
        if (sb.length() < 80) {
            appendField(sb, "JD", limit(job.getJdText(), 1000));
        }
        return limit(sb.toString(), 2000);
    }

    private String buildCandidateEvidenceDocument(Resume resume) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "候选人", resume.getCandidateName());
        appendField(sb, "意向岗位", resume.getIntendedPosition());
        appendField(sb, "年限", resume.getYearsExperience() == null ? null : resume.getYearsExperience() + "年");
        appendField(sb, "学历", resume.getEducation());
        appendField(sb, "学校", resume.getSchool());
        appendField(sb, "专业", resume.getMajor());

        JsonNode parsed = resume.getParsedJson();
        JsonNode source = structuredSource(parsed);
        if (source != null) {
            JsonNode basic = source.path("basicInfo");
            appendField(sb, "结构化意向", firstNonBlank(textOf(source, "intended_position"), textOf(basic, "intendedPosition")));
            appendField(sb, "结构化年限", firstNonBlank(textOf(source, "work_years"), textOf(basic, "workYears")));
            appendField(sb, "技能", jsonToCompactText(source.path("skills"), 700));
            appendField(sb, "工作经历", firstNonBlank(
                    jsonToCompactText(source.path("work_experience"), 900),
                    jsonToCompactText(source.path("workExperience"), 900)));
            appendField(sb, "项目经历", jsonToCompactText(source.path("projects"), 900));
            appendField(sb, "教育经历", jsonToCompactText(source.path("education"), 400));
        }
        if (parsed != null) {
            appendField(sb, "风险", jsonToCompactText(parsed.path("riskAssessment"), 400));
            appendField(sb, "潜力", jsonToCompactText(parsed.path("potentialAssessment"), 400));
        }
        if (sb.length() < 80) {
            appendField(sb, "简历原文", limit(resume.getRawText(), 1600));
        }
        return limit(sb.toString(), 3000);
    }

    private MatchAssessment llmAssess(JobProfile job,
                                      Resume resume,
                                      String jobProfileText,
                                      String evidenceText,
                                      double vectorScore,
                                      RankedResume ranked) {
        String sys = """
                你是招聘匹配评估器。请只基于给定岗位画像和候选人证据做证据化评分，不要编造简历中没有的信息。
                按 0-100 输出 skillScore、experienceScore、projectScore、softScore，并给出可给 HR 展示的解释。
                严格输出 JSON，格式：
                {
                  "skillScore": 80,
                  "experienceScore": 75,
                  "projectScore": 78,
                  "softScore": 70,
                  "matchedPoints": [{"requirement":"...","status":"MATCH/PARTIAL","evidence":"..."}],
                  "gaps": [{"requirement":"...","severity":"LOW/MEDIUM/HIGH","reason":"..."}],
                  "risks": ["..."],
                  "interviewQuestions": ["..."],
                  "summary": "..."
                }""";
        String user = "岗位画像:\n" + jobProfileText
                + "\n\n候选人证据:\n" + evidenceText
                + "\n\n检索信号: vectorScore=" + round2(vectorScore)
                + ", vectorRank=" + ranked.vectorRank()
                + ", rerankScore=" + round2(ranked.rerankScore())
                + ", rerankRank=" + ranked.rerankRank();
        try {
            String reply = deepSeekModelService.chatJson(sys, user);
            JsonNode parsed = JsonGuard.parseJsonSafe(reply);
            if (parsed != null && parsed.isObject()) {
                double skill = scoreField(parsed, "skillScore", "skill_score", 50);
                double experience = scoreField(parsed, "experienceScore", "experience_score", 50);
                double project = scoreField(parsed, "projectScore", "project_score", experience);
                double soft = scoreField(parsed, "softScore", "soft_score", 50);
                String summary = parsed.path("summary").asText("");
                if (summary.isBlank()) {
                    summary = parsed.path("reason").asText("");
                }
                return new MatchAssessment(skill, experience, project, soft, summary, parsed);
            }
        } catch (Exception e) {
            log.debug("llm assessment failed for resume {}: {}", resume.getId(), e.getMessage());
        }

        double base = Math.max(0.0, Math.min(100.0, 50.0 + vectorScore * 0.3));
        ObjectNode fallback = MAPPER.createObjectNode();
        fallback.put("summary", "LLM 证据化评分失败，已使用向量与重排信号降级评分。");
        fallback.putArray("matchedPoints");
        fallback.putArray("gaps");
        fallback.putArray("risks");
        fallback.putArray("interviewQuestions");
        return new MatchAssessment(base, base, base, base, fallback.path("summary").asText(), fallback);
    }

    private double scoreField(JsonNode node, String camelName, String snakeName, double defaultValue) {
        JsonNode value = node.has(camelName) ? node.path(camelName) : node.path(snakeName);
        return clampScore(value.asDouble(defaultValue));
    }

    private String decisionTier(double finalScore, MatchAssessment assessment) {
        if (assessment.skillScore() < 50 && assessment.experienceScore() < 50) {
            return "WEAK";
        }
        if (finalScore >= 85) return "STRONG_RECOMMEND";
        if (finalScore >= 75) return "RECOMMEND";
        if (finalScore >= 60) return "REVIEW";
        if (finalScore >= 45) return "WEAK";
        return "REJECT";
    }

    private boolean shouldCreateInterview(String tier) {
        return "STRONG_RECOMMEND".equals(tier) || "RECOMMEND".equals(tier);
    }

    private CandidateMatch saveMatch(JobProfile job, MatchRow row) {
        CandidateMatch cm = new CandidateMatch();
        cm.setJobId(job.getId());
        cm.setResumeId(row.ranked().resume().getId());
        cm.setSkillScore(BigDecimal.valueOf(row.assessment().skillScore()));
        cm.setExperienceScore(BigDecimal.valueOf(row.assessment().experienceScore()));
        cm.setSoftScore(BigDecimal.valueOf(row.assessment().softScore()));
        cm.setVectorScore(BigDecimal.valueOf(row.vectorScore()));
        cm.setOverallScore(BigDecimal.valueOf(row.finalScore()));
        cm.setMatchDetails(buildMatchDetails(job, row));
        cm.setCreatedAt(LocalDateTime.now());
        try {
            matchMapper.insert(cm);
        } catch (Exception e) {
            log.debug("insert match failed: {}", e.getMessage());
        }
        return cm;
    }

    private ObjectNode buildMatchDetails(JobProfile job, MatchRow row) {
        ObjectNode details = MAPPER.createObjectNode();
        RankedResume ranked = row.ranked();
        MatchAssessment assessment = row.assessment();
        details.put("version", VERSION);
        details.put("candidateName", ranked.resume().getCandidateName());
        details.put("jobTitle", job.getTitle());
        details.put("summary", assessment.summary());
        details.put("reason", assessment.summary());

        ObjectNode scoreBreakdown = details.putObject("scoreBreakdown");
        scoreBreakdown.put("skillScore", round2(assessment.skillScore()));
        scoreBreakdown.put("experienceScore", round2(assessment.experienceScore()));
        scoreBreakdown.put("projectScore", round2(assessment.projectScore()));
        scoreBreakdown.put("softScore", round2(assessment.softScore()));
        scoreBreakdown.put("vectorScore", round2(row.vectorScore()));
        scoreBreakdown.put("rerankScore", round2(ranked.rerankScore()));
        scoreBreakdown.put("finalScore", round2(row.finalScore()));

        ObjectNode weightConfig = details.putObject("weightConfig");
        MatchWeights weights = row.weights();
        weightConfig.put("skillScore", round2(weights.skillScore()));
        weightConfig.put("experienceScore", round2(weights.experienceScore()));
        weightConfig.put("projectScore", round2(weights.projectScore()));
        weightConfig.put("vectorScore", round2(weights.vectorScore()));
        weightConfig.put("rerankScore", round2(weights.rerankScore()));
        weightConfig.put("softScore", round2(weights.softScore()));
        weightConfig.put("total", round2(weights.total()));

        ObjectNode retrieval = details.putObject("retrieval");
        retrieval.put("vectorRank", ranked.vectorRank());
        retrieval.put("vectorScore", round2(row.vectorScore()));
        retrieval.put("candidateEvidence", buildCandidateEvidenceDocument(ranked.resume()));

        ObjectNode rerank = details.putObject("rerank");
        rerank.put("rank", ranked.rerankRank());
        rerank.put("score", round2(ranked.rerankScore()));
        rerank.put("applied", ranked.rerankApplied());

        copyAssessmentArray(details, assessment.details(), "matchedPoints");
        copyAssessmentArray(details, assessment.details(), "gaps");
        copyAssessmentArray(details, assessment.details(), "risks");
        copyAssessmentArray(details, assessment.details(), "interviewQuestions");

        ObjectNode decision = details.putObject("decision");
        decision.put("tier", row.tier());
        decision.put("createInterview", shouldCreateInterview(row.tier()));
        return details;
    }

    private void copyAssessmentArray(ObjectNode target, JsonNode source, String fieldName) {
        JsonNode value = source == null ? null : source.path(fieldName);
        if (value != null && value.isArray()) {
            target.set(fieldName, value);
        } else {
            target.set(fieldName, MAPPER.createArrayNode());
        }
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

    private JsonNode structuredSource(JsonNode parsed) {
        if (parsed == null || parsed.isNull() || parsed.isMissingNode()) {
            return null;
        }
        JsonNode structured = parsed.path("structuredData");
        return structured.isObject() ? structured : parsed;
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(label).append(": ").append(value);
    }

    private String textOf(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode node = parent.get(field);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.isTextual() ? node.asText() : node.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private String jsonToCompactText(JsonNode node, int maxLength) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            List<String> parts = new ArrayList<>();
            for (JsonNode item : array) {
                parts.add(item.isTextual() ? item.asText() : item.toString());
            }
            return limit(String.join("; ", parts), maxLength);
        }
        return limit(node.isTextual() ? node.asText() : node.toString(), maxLength);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String limit(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private double normalizeScore(double score) {
        if (score <= 1.0) {
            return clampScore(score * 100.0);
        }
        return clampScore(score);
    }

    private double rankScore(int zeroBasedRank) {
        return Math.max(0.0, 100.0 - zeroBasedRank * 5.0);
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round2(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class AssessmentThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "match-assessment-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    public record MatchWeights(double skillScore,
                               double experienceScore,
                               double projectScore,
                               double vectorScore,
                               double rerankScore,
                               double softScore) {

        public static MatchWeights defaultWeights() {
            return DEFAULT_WEIGHTS;
        }

        public static MatchWeights fromMap(Map<String, Double> weights) {
            if (weights == null || weights.isEmpty()) {
                return DEFAULT_WEIGHTS;
            }
            MatchWeights candidate = new MatchWeights(
                    valueOf(weights, "skillScore", "skill"),
                    valueOf(weights, "experienceScore", "experience"),
                    valueOf(weights, "projectScore", "project"),
                    valueOf(weights, "vectorScore", "vector"),
                    valueOf(weights, "rerankScore", "rerank"),
                    valueOf(weights, "softScore", "soft"));
            return normalize(candidate);
        }

        public static MatchWeights normalize(MatchWeights weights) {
            if (weights == null || weights.total() <= 0) {
                return DEFAULT_WEIGHTS;
            }
            double total = weights.total();
            if (Math.abs(total - 100.0) < 0.0001) {
                return weights;
            }
            return new MatchWeights(
                    weights.skillScore * 100.0 / total,
                    weights.experienceScore * 100.0 / total,
                    weights.projectScore * 100.0 / total,
                    weights.vectorScore * 100.0 / total,
                    weights.rerankScore * 100.0 / total,
                    weights.softScore * 100.0 / total);
        }

        public double weightedScore(MatchAssessment assessment, double vectorScore, double rerankScore) {
            return assessment.skillScore() * skillScore / 100.0
                    + assessment.experienceScore() * experienceScore / 100.0
                    + assessment.projectScore() * projectScore / 100.0
                    + vectorScore * vectorScore() / 100.0
                    + rerankScore * rerankScore() / 100.0
                    + assessment.softScore() * softScore / 100.0;
        }

        public double total() {
            return skillScore + experienceScore + projectScore + vectorScore + rerankScore + softScore;
        }

        public Map<String, Object> toPercentMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("skillScore", roundWeight(skillScore));
            out.put("experienceScore", roundWeight(experienceScore));
            out.put("projectScore", roundWeight(projectScore));
            out.put("vectorScore", roundWeight(vectorScore));
            out.put("rerankScore", roundWeight(rerankScore));
            out.put("softScore", roundWeight(softScore));
            out.put("total", roundWeight(total()));
            return out;
        }

        private static double valueOf(Map<String, Double> weights, String primary, String fallback) {
            Double value = weights.get(primary);
            if (value == null) {
                value = weights.get(fallback);
            }
            if (value == null || value.isNaN() || value < 0) {
                return 0.0;
            }
            return value;
        }

        private static double roundWeight(double value) {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    private record RankedResume(Resume resume, int vectorRank, int rerankRank, double rerankScore, boolean rerankApplied) {}

    private record MatchAssessment(double skillScore,
                                   double experienceScore,
                                   double projectScore,
                                   double softScore,
                                   String summary,
                                   JsonNode details) {}

    private record MatchRow(RankedResume ranked,
                            MatchAssessment assessment,
                            double vectorScore,
                            double finalScore,
                            String tier,
                            MatchWeights weights) {}
}
