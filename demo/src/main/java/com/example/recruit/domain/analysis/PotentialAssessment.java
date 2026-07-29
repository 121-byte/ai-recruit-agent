package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 第4轮:潜力评估结果 (rawJson 透传)。
 */
public class PotentialAssessment {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String rawJson;

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public static PotentialAssessment fromJson(String json) {
        PotentialAssessment data = new PotentialAssessment();
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
