package com.example.recruit.agent.tool;

import com.example.recruit.module.resume.domain.ComparisonResult;
import com.example.recruit.module.resume.domain.ResumeAnalysisResult;
import com.example.recruit.module.resume.application.ResumeAnalysisService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历分析工具 (rawJson 摘要 + 对比)。
 */
@Component
public class ResumeAnalysisTool {

    private final ResumeAnalysisService resumeAnalysisService;

    public ResumeAnalysisTool(ResumeAnalysisService resumeAnalysisService) {
        this.resumeAnalysisService = resumeAnalysisService;
    }

    @Tool(
            name = "analyzeResume",
            description = "全量解析简历，提取结构化数据、隐性洞察、风险评估。简历ID可用 searchResumes 按姓名/学校等条件查找",
            concurrencySafe = false)
    public Map<String, Object> analyzeResume(
            @ToolParam(name = "resumeId", description = "简历ID") Long resumeId) {
        if (resumeId == null) {
            return errorMap("简历ID不能为空");
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

        Map<String, Object> summary = new HashMap<>();
        summary.put("resumeId", result.getResumeId());
        summary.put("status", "analysis_complete");
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

    @Tool(name = "compareResumes", description = "对比分析多份简历，返回对比结果摘要")
    public Map<String, Object> compareResumes(
            @ToolParam(name = "resumeIds", description = "简历ID列表") List<Long> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return errorMap("简历ID列表不能为空");
        }
        if (resumeIds.size() < 2) {
            return errorMap("对比分析至少需要2份简历");
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
        Map<String, Object> summary = new HashMap<>();
        summary.put("resumeIds", result.getResumeIds());
        summary.put("status", "comparison_complete");
        summary.put("comparison", truncate(result.getComparisonResult(), 800));
        return summary;
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", message);
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }
}
