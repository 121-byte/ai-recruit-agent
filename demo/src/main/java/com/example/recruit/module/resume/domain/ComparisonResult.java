package com.example.recruit.module.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Date;
import java.util.List;

/**
 * 候选人对比结果 (LLM 对比 JSON 透传)。
 */
public class ComparisonResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<Long> resumeIds;
    private String comparisonResult;  // LLM 返回的对比 JSON 字符串
    private Date comparedAt;

    public JsonNode toJsonNode() {
        ObjectNode node = MAPPER.createObjectNode();
        ArrayNode ids = node.putArray("resumeIds");
        if (resumeIds != null) resumeIds.forEach(ids::add);
        node.put("comparedAt", comparedAt != null ? comparedAt.toString() : "");
        try {
            node.set("comparison", MAPPER.readTree(comparisonResult));
        } catch (Exception e) {
            node.put("comparison", comparisonResult);
        }
        return node;
    }

    public List<Long> getResumeIds() { return resumeIds; }
    public void setResumeIds(List<Long> resumeIds) { this.resumeIds = resumeIds; }
    public String getComparisonResult() { return comparisonResult; }
    public void setComparisonResult(String comparisonResult) { this.comparisonResult = comparisonResult; }
    public Date getComparedAt() { return comparedAt; }
    public void setComparedAt(Date comparedAt) { this.comparedAt = comparedAt; }
}
