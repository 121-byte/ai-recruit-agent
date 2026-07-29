package com.example.recruit.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

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

    /**
     * 从文本中提取第一个 {@code {...}} 或 {@code [...]} JSON 块。
     *
     * @return 提取到的 JSON 字符串，无则 null
     */
    public static String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 优先匹配 {...} 块
        int objStart = text.indexOf('{');
        if (objStart >= 0) {
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            for (int i = objStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == '\\' && inStr) {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    inStr = !inStr;
                    continue;
                }
                if (inStr) {
                    continue;
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(objStart, i + 1);
                    }
                }
            }
        }
        // 其次匹配 [...] 数组块
        int arrStart = text.indexOf('[');
        if (arrStart >= 0) {
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            for (int i = arrStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (esc) {
                    esc = false;
                    continue;
                }
                if (c == '\\' && inStr) {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    inStr = !inStr;
                    continue;
                }
                if (inStr) {
                    continue;
                }
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(arrStart, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析 JSON 并校验含指定字段，合法返回 JsonNode，否则 null。
     */
    public static JsonNode parseAndValidate(String text, String requiredField) {
        JsonNode node = parseJsonSafe(text);
        if (node == null || requiredField == null || requiredField.isBlank()) {
            return node;
        }
        JsonNode field = node.path(requiredField);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        return node;
    }

    // ═══════════════════ 非法内容检测 (§9.3 prompt-injection 防护) ═══════════════════

    private static final Pattern ILLEGAL_IGNORE =
            Pattern.compile("(?i)ignore\\s+(previous|prior|above|all)\\s*(instruction|prompt|rule|order)");
    private static final Pattern ILLEGAL_SYSTEM_TAG =
            Pattern.compile("</?system>");
    private static final Pattern ILLEGAL_SYSTEM_KEYWORD =
            Pattern.compile("(?i)\\bsystem\\b");
    private static final Pattern ILLEGAL_NEW_IDENTITY =
            Pattern.compile("(?i)(you\\s+are\\s+(now|a\\s+new)|new\\s+instruction|act\\s+as\\s+if)");
    private static final Pattern ILLEGAL_REVEAL_PROMPT =
            Pattern.compile("(?i)(reveal|show|print|repeat).{0,10}(system\\s+prompt|initial\\s+instruction)");

    /**
     * 检测文本是否含 prompt injection / 非法越狱内容。
     * <ul>
     *   <li>"ignore previous/prior/above/all instruction/prompt/rule/order"</li>
     *   <li>{@code </system>} / {@code <system>} 标签</li>
     *   <li>重复出现的 system 关键词 (≥3 次)</li>
     *   <li>"you are now/new instruction/act as if" 等越狱措辞</li>
     *   <li>要求泄露 system prompt 的措辞</li>
     * </ul>
     */
    public static boolean containsIllegalContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (ILLEGAL_IGNORE.matcher(text).find()) {
            return true;
        }
        if (ILLEGAL_SYSTEM_TAG.matcher(text).find()) {
            return true;
        }
        if (ILLEGAL_NEW_IDENTITY.matcher(text).find()) {
            return true;
        }
        if (ILLEGAL_REVEAL_PROMPT.matcher(text).find()) {
            return true;
        }
        // system 关键词重复出现 ≥3 次
        java.util.regex.Matcher m = ILLEGAL_SYSTEM_KEYWORD.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
            if (count >= 3) {
                return true;
            }
        }
        return false;
    }
}
