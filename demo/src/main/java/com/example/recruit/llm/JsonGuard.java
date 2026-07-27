package com.example.recruit.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * JSON 解析防护工具 (复刻自文档 §9.3)。
 *
 * <p>处理 LLM 输出中常见的 JSON 格式问题：
 * <ol>
 *   <li>直接尝试解析</li>
 *   <li>失败则提取第一个 {@code {...}} 块再解析</li>
 *   <li>仍失败返回 null</li>
 * </ol>
 */
@Component
public final class JsonGuard {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonGuard() {
    }

    public static JsonNode parseJsonSafe(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 1. 直接尝试解析
        try {
            return MAPPER.readTree(text);
        } catch (Exception ignored) {
            // fall through
        }

        // 2. 提取第一个 {...} 块
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return MAPPER.readTree(text.substring(start, end + 1));
            } catch (Exception ignored) {
                // fall through
            }
        }

        // 3. 也尝试提取 [...] 数组
        int as = text.indexOf('[');
        int ae = text.lastIndexOf(']');
        if (as >= 0 && ae > as) {
            try {
                return MAPPER.readTree(text.substring(as, ae + 1));
            } catch (Exception ignored) {
                // give up
            }
        }

        return null;
    }

    /** 宽松提取指定字段为文本。 */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
