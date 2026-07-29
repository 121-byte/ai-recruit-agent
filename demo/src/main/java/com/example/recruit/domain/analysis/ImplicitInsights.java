package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 第2轮:隐性能力挖掘结果 (rawJson 透传)。
 */
public class ImplicitInsights {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String rawJson;

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public static ImplicitInsights fromJson(String json) {
        ImplicitInsights data = new ImplicitInsights();
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
