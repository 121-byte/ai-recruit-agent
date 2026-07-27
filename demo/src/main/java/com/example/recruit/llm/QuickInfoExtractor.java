package com.example.recruit.llm;

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
            "Java", "Spring", "Spring Boot", "Spring Cloud", "MyBatis", "MyBatis-Plus",
            "Python", "Django", "Flask", "FastAPI",
            "JavaScript", "TypeScript", "Vue", "React", "Angular", "Node",
            "Go", "Golang", "Rust", "C++", "C#", ".NET",
            "MySQL", "PostgreSQL", "Redis", "MongoDB", "Elasticsearch", "Kafka", "RabbitMQ",
            "Docker", "Kubernetes", "K8s", "CI/CD", "Jenkins",
            "Hadoop", "Spark", "Flink", "Hive", "ClickHouse",
            "PyTorch", "TensorFlow", "LLM", "RAG", "Agent",
            "Linux", "Git", "Maven", "Gradle"
    );

    private static final Pattern NAME_HINT = Pattern.compile(
            "(?:姓名|名字|Name|name)\\s*[:：]\\s*([\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z\\s]{1,19})");

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
}
