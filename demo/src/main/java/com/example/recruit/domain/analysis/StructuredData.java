package com.example.recruit.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历结构化数据 POJO (复刻对齐清单 §1)。
 * 封装 LLM 解析后的候选人基础信息。
 */
@Data
public class StructuredData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String name;
    private String phone;
    private String email;
    private String summary;
    private String intendedPosition;
    private String workYears;
    private List<String> skills = new ArrayList<>();
    private List<Map<String, Object>> workExperience = new ArrayList<>();
    private List<Map<String, Object>> education = new ArrayList<>();

    /** 从 JsonNode 反序列化。 */
    public static StructuredData fromJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(json, StructuredData.class);
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

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("phone", phone);
        m.put("email", email);
        m.put("summary", summary);
        m.put("intendedPosition", intendedPosition);
        m.put("workYears", workYears);
        m.put("skills", skills);
        m.put("workExperience", workExperience);
        m.put("education", education);
        return m;
    }
}
