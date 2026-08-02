package com.example.recruit.agent.tool;

import com.example.recruit.dal.entity.Resume;
import com.example.recruit.module.resume.application.ResumeAnalysisService;
import com.example.recruit.module.resume.application.ResumeService;
import com.example.recruit.module.resume.domain.ComparisonResult;
import com.example.recruit.module.resume.domain.ResumeAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResumeAnalysisTool {

    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeService resumeService;

    public ResumeAnalysisTool(ResumeAnalysisService resumeAnalysisService,
                              ResumeService resumeService) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.resumeService = resumeService;
    }

    @Tool(
            name = "analyzeResume",
            description = "快速查看简历详情和结构化分析。优先读取数据库 parsed_json 缓存；没有缓存时只返回数据库基础简历信息，不自动重新全量解析。",
            concurrencySafe = false)
    public Map<String, Object> analyzeResume(
            @ToolParam(name = "resumeId", description = "简历 ID")
            Long resumeId) {
        if (resumeId == null) {
            return errorMap("简历 ID 不能为空");
        }

        Resume resume = resumeService.getById(resumeId);
        if (resume == null) {
            return errorMap("简历不存在: " + resumeId);
        }

        if (hasParsedJson(resume.getParsedJson())) {
            return cachedResumeDetail(resume);
        }

        return basicResumeDetail(resume);
    }

    @Tool(
            name = "refreshResumeAnalysis",
            description = "显式重新全量解析简历并写回数据库。只有用户明确要求重新分析、重新解析或刷新简历画像时才调用。",
            concurrencySafe = false)
    public Map<String, Object> refreshResumeAnalysis(
            @ToolParam(name = "resumeId", description = "简历 ID")
            Long resumeId) {
        if (resumeId == null) {
            return errorMap("简历 ID 不能为空");
        }

        ResumeAnalysisResult result;
        try {
            result = resumeAnalysisService.analyzeFull(resumeId);
        } catch (Exception e) {
            return errorMap("简历解析失败: " + e.getMessage());
        }
        if (result == null) {
            return errorMap("简历 " + resumeId + " 不存在或解析失败");
        }

        return summarizeAnalysisResult(result, "generated_analysis");
    }

    @Tool(
            name = "compareResumes",
            description = "对比分析多份简历，返回对比结果摘要。")
    public Map<String, Object> compareResumes(
            @ToolParam(name = "resumeIds", description = "简历 ID 列表")
            List<Long> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return errorMap("简历 ID 列表不能为空");
        }
        if (resumeIds.size() < 2) {
            return errorMap("对比分析至少需要 2 份简历");
        }

        ComparisonResult result;
        try {
            result = resumeAnalysisService.compareResumes(resumeIds);
        } catch (Exception e) {
            return errorMap("简历对比失败: " + e.getMessage());
        }
        if (result == null) {
            return errorMap("简历对比失败");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resumeIds", result.getResumeIds());
        summary.put("status", "comparison_complete");
        summary.put("comparison", truncate(result.getComparisonResult(), 800));
        return summary;
    }

    private Map<String, Object> cachedResumeDetail(Resume resume) {
        JsonNode parsed = resume.getParsedJson();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resumeId", resume.getId());
        summary.put("name", resume.getCandidateName());
        summary.put("status", resume.getStatus());
        summary.put("source", "cached_parsed_json");
        summary.put("school", resume.getSchool());
        summary.put("education", resume.getEducation());
        summary.put("major", resume.getMajor());
        summary.put("yearsExperience", resume.getYearsExperience());
        summary.put("intendedPosition", resume.getIntendedPosition());

        putIfPresent(summary, "structuredData", parsed.path("structuredData"), 500);
        putIfPresent(summary, "implicitInsights", parsed.path("implicitInsights"), 500);
        putIfPresent(summary, "riskAssessment", parsed.path("riskAssessment"), 300);
        putIfPresent(summary, "potentialAssessment", parsed.path("potentialAssessment"), 300);
        putIfPresent(summary, "validation", parsed.path("validation"), 200);

        if (!summary.containsKey("structuredData")) {
            putIfPresent(summary, "parsedJson", parsed, 800);
        }
        return summary;
    }

    private Map<String, Object> basicResumeDetail(Resume resume) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resumeId", resume.getId());
        summary.put("name", resume.getCandidateName());
        summary.put("status", resume.getStatus());
        summary.put("source", "database_basic");
        summary.put("parsed_profile_available", false);
        summary.put("school", resume.getSchool());
        summary.put("education", resume.getEducation());
        summary.put("major", resume.getMajor());
        summary.put("yearsExperience", resume.getYearsExperience());
        summary.put("intendedPosition", resume.getIntendedPosition());
        summary.put("rawText", truncate(resume.getRawText(), 1200));
        summary.put("hint", "如需重新生成结构化简历画像，请调用 refreshResumeAnalysis");
        return summary;
    }

    private Map<String, Object> summarizeAnalysisResult(ResumeAnalysisResult result, String source) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resumeId", result.getResumeId());
        summary.put("status", "analysis_complete");
        summary.put("source", source);
        summary.put("validation", truncate(result.getValidation(), 200));
        if (result.getStructuredData() != null) {
            summary.put("structuredData", truncate(result.getStructuredData().getRawJson(), 500));
        }
        if (result.getImplicitInsights() != null) {
            summary.put("implicitInsights", truncate(result.getImplicitInsights().getRawJson(), 500));
        }
        if (result.getRiskAssessment() != null) {
            summary.put("riskAssessment", truncate(result.getRiskAssessment().getRawJson(), 300));
        }
        if (result.getPotentialAssessment() != null) {
            summary.put("potentialAssessment", truncate(result.getPotentialAssessment().getRawJson(), 300));
        }
        return summary;
    }

    private static void putIfPresent(Map<String, Object> out, String key, JsonNode node, int max) {
        if (!hasParsedJson(node)) {
            return;
        }
        out.put(key, truncate(node.isTextual() ? node.asText() : node.toString(), max));
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
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
