package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 第1轮:结构化提取结果。
 * rawJson 模式:LLM 返回的 JSON 原样存储, 不丢失任何字段。
 */
public class StructuredData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String rawJson;

    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public static StructuredData fromJson(String json) {
        StructuredData data = new StructuredData();
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
