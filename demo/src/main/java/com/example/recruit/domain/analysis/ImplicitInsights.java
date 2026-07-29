package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 隐性特质洞察 POJO (复刻对齐清单 §1)。
 * 描述从简历中推断出的潜在领导力、学习敏锐度、文化契合度等隐性特质。
 */
@Data
public class ImplicitInsights {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String leadershipPotential;
    private String learningAgility;
    private String cultureFit;
    private String motivation;
    private List<String> notes = new ArrayList<>();

    /** 从 JsonNode 反序列化。 */
    public static ImplicitInsights fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, ImplicitInsights.class);
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
