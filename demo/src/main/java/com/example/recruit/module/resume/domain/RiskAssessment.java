package com.example.recruit.module.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 第3轮:风险识别结果 (rawJson 透传)。
 */
public class RiskAssessment {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String rawJson;

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public static RiskAssessment fromJson(String json) {
        RiskAssessment data = new RiskAssessment();
        data.rawJson = json;
        return data;
    }

    public JsonNode toJsonNode() {
        try {
            return MAPPER.readTree(rawJson);
        } catch (Exception e) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("raw", rawJson);
            return node;
        }
    }

    public String toJson() {
        return rawJson;
    }
}
