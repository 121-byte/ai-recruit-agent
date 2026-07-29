package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 潜力评估 POJO (复刻对齐清单 §1)。
 * 描述候选人成长上限、领导力、创新力等潜力维度。
 */
@Data
public class PotentialAssessment {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String growthCeiling;
    private String leadership;
    private String innovation;
    private String adaptability;
    private String summary;
    private List<String> highlights = new ArrayList<>();

    /** 从 JsonNode 反序列化。 */
    public static PotentialAssessment fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, PotentialAssessment.class);
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
