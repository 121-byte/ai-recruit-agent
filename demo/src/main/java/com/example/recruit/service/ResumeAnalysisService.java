package com.example.recruit.service;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.mapper.ResumeMapper;
import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.JsonGuard;
import com.example.recruit.llm.QuickInfoExtractor;
import com.example.recruit.service.analysis.ComparisonResult;
import com.example.recruit.service.analysis.ImplicitInsights;
import com.example.recruit.service.analysis.PotentialAssessment;
import com.example.recruit.service.analysis.ResumeAnalysisResult;
import com.example.recruit.service.analysis.RiskAssessment;
import com.example.recruit.service.analysis.StructuredData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历分析业务服务 (复刻对齐清单 §4.5)。
 *
 * <p>封装 LLM 解析 raw_text 为结构化数据、写回 parsedJson + embedding，
 * 以及多简历横向对比。Tool 层不再持有 Mapper/LLM 依赖。
 */
@Service
public class ResumeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResumeMapper resumeMapper;
    private final DeepSeekModelService deepSeek;
    private final EmbeddingService embeddingService;
    private final QuickInfoExtractor quickInfoExtractor;

    public ResumeAnalysisService(ResumeMapper resumeMapper,
                                 DeepSeekModelService deepSeek,
                                 EmbeddingService embeddingService,
                                 QuickInfoExtractor quickInfoExtractor) {
        this.resumeMapper = resumeMapper;
        this.deepSeek = deepSeek;
        this.embeddingService = embeddingService;
        this.quickInfoExtractor = quickInfoExtractor;
    }

    /**
     * 全量分析单份简历：LLM 解析结构化 → 装载 StructuredData + 隐性/潜力/风险
     * → embedding 写回 → 返回 ResumeAnalysisResult。Mock/key 缺失时降级。
     */
    public ResumeAnalysisResult analyzeFull(Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        Resume r = resumeMapper.selectById(resumeId);
        if (r == null) {
            return null;
        }

        String rawText = r.getRawText() == null ? "" : r.getRawText();
        ObjectNode merged = quickInfoExtractor.extract(rawText);

        String sys = """
                你是简历解析器与分析师。从简历文本提取结构化信息并完成深度分析，以 JSON 输出:
                {"structured":{"name":"...","phone":"...","email":"...","intendedPosition":"...","workYears":0,
                  "skills":["..."],"workExperience":[{"company":"...","position":"...","duration":"...","description":"..."}],
                  "education":[{"school":"...","major":"...","degree":"..."}]},
                 "implicitInsights":{"leadershipPotential":"...","learningAgility":"...","cultureFit":"...","motivation":"...","notes":["..."]},
                 "potentialAssessment":{"growthCeiling":"...","leadership":"...","innovation":"...","adaptability":"...","summary":"...","highlights":["..."]},
                 "riskAssessment":{"level":"low/medium/high","factors":["..."],"mitigation":["..."]}}
                不要 markdown 标记。""";
        JsonNode root = null;
        try {
            String reply = deepSeek.chatJson(sys, rawText);
            root = JsonGuard.parseJsonSafe(reply);
        } catch (Exception e) {
            log.warn("analyzeFull chat failed: {}", e.getMessage());
        }

        StructuredData structured = null;
        ImplicitInsights implicit = null;
        PotentialAssessment potential = null;
        RiskAssessment risk = null;

        if (root != null && root.isObject()) {
            // 合并 structured：LLM 缺字段则用 quickInfo 补
            JsonNode structNode = root.path("structured");
            if (structNode.isObject()) {
                for (String field : new String[]{"name", "phone", "email"}) {
                    JsonNode v = structNode.path(field);
                    if ((v.isMissingNode() || v.asText("").isEmpty()) && merged.has(field)) {
                        ((ObjectNode) structNode).set(field, merged.get(field));
                    }
                }
            } else {
                structNode = merged;
            }
            structured = StructuredData.fromJson(structNode);
            implicit = ImplicitInsights.fromJson(root.path("implicitInsights"));
            potential = PotentialAssessment.fromJson(root.path("potentialAssessment"));
            risk = RiskAssessment.fromJson(root.path("riskAssessment"));

            // parsedJson 写为 structured (或整棵 root，简化用 structured)
            try {
                r.setParsedJson(structNode);
            } catch (Exception e) {
                log.warn("setParsedJson failed: {}", e.getMessage());
            }
        } else {
            // LLM 失败，至少回写 quickInfo
            try {
                r.setParsedJson(merged);
            } catch (Exception e) {
                log.warn("setParsedJson fallback failed: {}", e.getMessage());
            }
        }

        // embedding 写回
        try {
            r.setEmbedding(embeddingService.embed(rawText));
        } catch (Throwable ignored) {
            log.debug("embed resume failed");
        }
        r.setStatus("reviewed");
        r.setUpdatedAt(LocalDateTime.now());
        try {
            resumeMapper.updateById(r);
        } catch (Exception e) {
            log.warn("updateById resume failed: {}", e.getMessage());
        }

        ResumeAnalysisResult result = new ResumeAnalysisResult();
        result.setStructuredData(structured);
        result.setImplicitInsights(implicit);
        result.setPotentialAssessment(potential);
        result.setRiskAssessment(risk);
        return result;
    }

    /**
     * 横向对比多份简历：取各 Resume → LLM chatJson 对比 → 返回 ComparisonResult。
     */
    public ComparisonResult compareResumes(List<Long> resumeIds) {
        ComparisonResult result = new ComparisonResult();
        if (resumeIds == null || resumeIds.isEmpty()) {
            return result;
        }
        List<Resume> resumes = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        for (Long id : resumeIds) {
            try {
                Resume r = resumeMapper.selectById(id);
                if (r != null) {
                    resumes.add(r);
                    candidates.add(r.getCandidateName() == null ? ("#" + id) : r.getCandidateName());
                }
            } catch (Exception e) {
                log.warn("load resume {} failed: {}", id, e.getMessage());
            }
        }
        result.setCandidates(candidates);
        if (resumes.isEmpty()) {
            return result;
        }

        StringBuilder user = new StringBuilder();
        for (int i = 0; i < resumes.size(); i++) {
            Resume r = resumes.get(i);
            String raw = r.getRawText() == null ? "" : r.getRawText();
            if (raw.length() > 400) {
                raw = raw.substring(0, 400);
            }
            user.append("候选人").append(i + 1).append(": ").append(r.getCandidateName())
                    .append("\n").append(raw).append("\n\n");
        }
        String sys = "你是招聘对比分析师。对多份简历横向对比，以 JSON 输出: {\"candidates\":[\"...\"],\"scores\":{\"name\":0},\"summary\":\"...\"}";
        try {
            String reply = deepSeek.chatJson(sys, user.toString());
            JsonNode node = JsonGuard.parseJsonSafe(reply);
            if (node != null) {
                ComparisonResult parsed = ComparisonResult.fromJson(node);
                if (parsed != null) {
                    if (parsed.getCandidates() != null && !parsed.getCandidates().isEmpty()) {
                        result.setCandidates(parsed.getCandidates());
                    }
                    if (parsed.getScores() != null) {
                        result.setScores(parsed.getScores());
                    }
                    result.setSummary(parsed.getSummary());
                }
            }
        } catch (Exception e) {
            log.warn("compareResumes chat failed: {}", e.getMessage());
        }
        return result;
    }
}
