package com.example.recruit.infra.fileparser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 快速信息提取器 (复刻自文档 §9.4)。
 *
 * <p>从简历 raw_text 中用正则快速提取关键信息（姓名、电话、邮箱、技能列表），
 * 用于简历上传后的预填充，避免每次都调 LLM。
 */
@Component
public class QuickInfoExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern PHONE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.-]+@[\\w.-]+\\.\\w{2,}\\b");

    /** 常见技能词表，用于从 raw_text 快速识别技能。 */
    private static final List<String> SKILL_VOCAB = List.of(
            "Java", "Spring", "Spring Boot", "Spring Cloud", "SpringCloud", "MyBatis", "MyBatis-Plus",
            "Python", "Django", "Flask", "FastAPI",
            "JavaScript", "TypeScript", "Vue", "React", "Angular", "Node", "Node.js",
            "Go", "Golang", "Rust", "C++", "C#", ".NET",
            "MySQL", "PostgreSQL", "Redis", "MongoDB", "Elasticsearch", "Kafka", "RabbitMQ",
            "Docker", "Kubernetes", "K8s", "CI/CD", "Jenkins",
            "Hadoop", "Spark", "Flink", "Hive", "ClickHouse",
            "PyTorch", "TensorFlow", "LLM", "RAG", "Agent",
            "Linux", "Git", "Maven", "Gradle"
    );

    private static final Pattern NAME_HINT = Pattern.compile(
            "(?:姓名|名字|Name|name)\\s*[:：]\\s*([\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z\\s]{1,19})");

    /** 学历关键词。 */
    private static final Pattern EDUCATION_HINT = Pattern.compile(
            "(本科|硕士|博士|大专|专科|Bachelor|Master|PhD|Doctor)");
    /** 院校名称。 */
    private static final Pattern SCHOOL_HINT = Pattern.compile(
            "(\\S{2,20}(?:大学|学院|University|College))");
    /** 工作年限：如 "5年" / "3 years"。 */
    private static final Pattern WORK_YEARS = Pattern.compile(
            "(\\d+)\\s*(年|years?)", Pattern.CASE_INSENSITIVE);
    /** 意向岗位：如 "意向岗位: Java工程师"。 */
    private static final Pattern INTENDED_POS = Pattern.compile(
            "(意向|求职|目标)\\s*[:：]?\\s*(\\S{2,30})");

    public ObjectNode extract(String rawText) {
        ObjectNode result = MAPPER.createObjectNode();
        if (rawText == null || rawText.isBlank()) {
            return result;
        }

        // 姓名
        Matcher nameM = NAME_HINT.matcher(rawText);
        if (nameM.find()) {
            result.put("name", nameM.group(1).trim());
        }

        // 电话
        Matcher phoneM = PHONE.matcher(rawText);
        if (phoneM.find()) {
            result.put("phone", phoneM.group());
        }

        // 邮箱
        Matcher emailM = EMAIL.matcher(rawText);
        if (emailM.find()) {
            result.put("email", emailM.group());
        }

        // 技能
        ArrayNode skills = MAPPER.createArrayNode();
        String lower = rawText.toLowerCase();
        for (String skill : SKILL_VOCAB) {
            if (lower.contains(skill.toLowerCase())) {
                skills.add(skill);
            }
        }
        if (!skills.isEmpty()) {
            result.set("skills", skills);
        }

        // 教育
        String education = extractEducation(rawText);
        if (education != null) {
            result.put("education", education);
        }

        // 工作年限
        Integer workYears = extractWorkYears(rawText);
        if (workYears != null) {
            result.put("workYears", workYears);
        }

        // 意向岗位
        String intendedPosition = extractIntendedPosition(rawText);
        if (intendedPosition != null) {
            result.put("intendedPosition", intendedPosition);
        }

        return result;
    }

    /** 便捷：返回技能列表。 */
    public List<String> extractSkills(String rawText) {
        List<String> hits = new ArrayList<>();
        if (rawText == null) {
            return hits;
        }
        String lower = rawText.toLowerCase();
        for (String skill : SKILL_VOCAB) {
            if (lower.contains(skill.toLowerCase())) {
                hits.add(skill);
            }
        }
        return hits;
    }

    /**
     * 提取教育信息：匹配学历关键词 + 院校名称，拼成 "学历 @ 院校"。
     * 仅当至少命中学历关键词时返回；院校可缺省。
     */
    public String extractEducation(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        Matcher eduM = EDUCATION_HINT.matcher(rawText);
        if (!eduM.find()) {
            return null;
        }
        String degree = eduM.group(1);
        // 在学历附近（向后 80 字符内）找院校，找不到则全局兜底
        String school = null;
        Matcher schoolM = SCHOOL_HINT.matcher(rawText);
        while (schoolM.find()) {
            school = schoolM.group(1);
            int schoolStart = schoolM.start(1);
            if (Math.abs(schoolStart - eduM.start()) <= 80) {
                break;
            }
        }
        return school == null ? degree : degree + " @ " + school;
    }

    /** 提取工作年限（取首个匹配的数字）。 */
    public Integer extractWorkYears(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        Matcher m = WORK_YEARS.matcher(rawText);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 提取意向岗位。 */
    public String extractIntendedPosition(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        Matcher m = INTENDED_POS.matcher(rawText);
        if (m.find()) {
            return m.group(2).trim();
        }
        return null;
    }
}
