package com.example.recruit.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 简历分析结果聚合 POJO (复刻对齐清单 §1)。
 * 汇总结构化数据、隐性洞察、潜力评估、风险评估与对比结果。
 */
@Data
public class ResumeAnalysisResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredData structuredData;
    private ImplicitInsights implicitInsights;
    private PotentialAssessment potentialAssessment;
    private RiskAssessment riskAssessment;
    private ComparisonResult comparisonResult;

    /** 从 JsonNode 反序列化。 */
    public static ResumeAnalysisResult fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, ResumeAnalysisResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 序列化为 JSON 字符串。 */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 转换为 JsonNode。 */
    public JsonNode toJsonNode() {
        try {
            return MAPPER.convertValue(this, JsonNode.class);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }
}
