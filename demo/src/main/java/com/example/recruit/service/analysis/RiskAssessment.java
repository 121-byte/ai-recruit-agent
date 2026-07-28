package com.example.recruit.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估 POJO (复刻对齐清单 §1)。
 * 描述候选人潜在风险等级、风险因素与缓释建议。
 */
@Data
public class RiskAssessment {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** low/medium/high */
    private String level;
    private List<String> factors = new ArrayList<>();
    private List<String> mitigation = new ArrayList<>();

    /** 从 JsonNode 反序列化。 */
    public static RiskAssessment fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, RiskAssessment.class);
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
