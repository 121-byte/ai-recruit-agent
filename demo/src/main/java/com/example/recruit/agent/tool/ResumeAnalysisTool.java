package com.example.recruit.agent.tool;

import com.example.recruit.service.ResumeAnalysisService;
import com.example.recruit.service.analysis.ResumeAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历分析工具 (复刻自文档 §8 ResumeAnalysisTool)。
 *
 * <p>薄封装：调用 {@link ResumeAnalysisService#analyzeFull(Long)}，结果 truncate。
 * Tool 不再注入 DeepSeek/Embedding/Mapper。
 */
@Component
public class ResumeAnalysisTool {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final ResumeAnalysisService resumeAnalysisService;

    public ResumeAnalysisTool(ResumeAnalysisService resumeAnalysisService) {
        this.resumeAnalysisService = resumeAnalysisService;
    }

    @Tool(
            name = "analyzeResume",
            description = "LLM 解析简历，提取结构化字段（姓名/意向岗位/工作年限/技能列表/工作经历/教育）并写回。",
            concurrencySafe = false)
    public Object analyzeResume(
            @ToolParam(name = "resumeId", description = "简历 ID")
            Long resumeId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (resumeId == null) {
            out.put("error", "resumeId 不能为空");
            return out;
        }
        ResumeAnalysisResult result = resumeAnalysisService.analyzeFull(resumeId);
        if (result == null) {
            out.put("error", "简历不存在或分析失败: " + resumeId);
            return out;
        }
        out.put("resume_id", resumeId);
        if (result.getStructuredData() != null) {
            out.put("name", result.getStructuredData().getName());
            out.put("parsed_json", result.getStructuredData().toJsonNode());
        }
        out.put("implicit_insights", result.getImplicitInsights() == null ? null : result.getImplicitInsights().toJsonNode());
        out.put("potential_assessment", result.getPotentialAssessment() == null ? null : result.getPotentialAssessment().toJsonNode());
        out.put("risk_assessment", result.getRiskAssessment() == null ? null : result.getRiskAssessment().toJsonNode());
        out.put("status", "reviewed");
        return out;
    }
}
