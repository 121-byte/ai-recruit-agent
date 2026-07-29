package com.example.recruit.module.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Date;

/**
 * 简历全量解析结果 (4 轮 + 自校验)。
 */
public class ResumeAnalysisResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long resumeId;
    private StructuredData structuredData;
    private ImplicitInsights implicitInsights;
    private RiskAssessment riskAssessment;
    private PotentialAssessment potentialAssessment;
    private String validation;       // 自校验结论
    private Date analyzedAt;

    public JsonNode toJsonNode() {
        ObjectNode node = MAPPER.createObjectNode();
        if (resumeId != null) {
            node.put("resumeId", resumeId);
        } else {
            node.putNull("resumeId");
        }
        node.put("validation", validation);
        node.put("analyzedAt", analyzedAt != null ? analyzedAt.toString() : "");
        if (structuredData != null) node.set("structuredData", structuredData.toJsonNode());
        if (implicitInsights != null) node.set("implicitInsights", implicitInsights.toJsonNode());
        if (riskAssessment != null) node.set("riskAssessment", riskAssessment.toJsonNode());
        if (potentialAssessment != null) node.set("potentialAssessment", potentialAssessment.toJsonNode());
        return node;
    }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public StructuredData getStructuredData() { return structuredData; }
    public void setStructuredData(StructuredData structuredData) { this.structuredData = structuredData; }
    public ImplicitInsights getImplicitInsights() { return implicitInsights; }
    public void setImplicitInsights(ImplicitInsights implicitInsights) { this.implicitInsights = implicitInsights; }
    public RiskAssessment getRiskAssessment() { return riskAssessment; }
    public void setRiskAssessment(RiskAssessment riskAssessment) { this.riskAssessment = riskAssessment; }
    public PotentialAssessment getPotentialAssessment() { return potentialAssessment; }
    public void setPotentialAssessment(PotentialAssessment potentialAssessment) { this.potentialAssessment = potentialAssessment; }
    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }
    public Date getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Date analyzedAt) { this.analyzedAt = analyzedAt; }
}
