package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人对比结果 POJO (复刻对齐清单 §1)。
 * 描述多名候选人的对比评分与综合结论。
 */
@Data
public class ComparisonResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<String> candidates = new ArrayList<>();
    private Map<String, Object> scores = new LinkedHashMap<>();
    private String summary;

    /** 从 JsonNode 反序列化。 */
    public static ComparisonResult fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, ComparisonResult.class);
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
