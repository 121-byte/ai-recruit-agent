package com.example.recruit.memory;

import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 自动记忆提取 (复刻自文档 §5.4，110 行)。
 *
 * <p>每轮对话结束后调用 {@link #extract}，从 (userMessage, assistantReply) 中
 * 经 LLM 提取值得长期记住的信息 (用户身份/角色、技术偏好、经验要求、明确纠正)，
 * 写入 longTermMemory。UNIQUE(agent_id, memory_key) 约束自动去重。
 *
 * <p>预过滤：userMessage &lt; 5 字符 → 跳过；未命中触发关键词 → 跳过，
 * 避免对每条消息都调用 LLM 浪费 token。
 */
@Component
public class AutoMemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(AutoMemoryExtractor.class);

    private static final int MIN_MESSAGE_LENGTH = 5;
    private static final int MAX_INPUT_CHARS = 500;

    private static final Set<String> TRIGGER_KEYWORDS = Set.of(
            "偏好", "喜欢", "希望", "要求", "需要", "不要", "必须",
            "我是", "我叫", "我负责", "我们公司", "我们团队",
            "记住", "以后都", "默认", "经验", "技术栈",
            "纠正", "不是", "应该是", "重新"
    );

    private static final String SYSTEM_PROMPT =
            "从对话中提取值得长期记住的信息。只提取：用户身份/角色、技术偏好、经验要求、明确纠正。" +
            "输出 JSON：{\"memories\":[{\"key\":\"snake_case_key\"," +
            "\"value\":\"简短描述\",\"category\":\"preference|fact|note\"}]}。" +
            "若无值得提取的信息，返回 {\"memories\":[]}。";

    private final DeepSeekModelService deepSeek;
    private final PostgresLongTermMemory longTermMemory;

    public AutoMemoryExtractor(DeepSeekModelService deepSeek,
                               PostgresLongTermMemory longTermMemory) {
        this.deepSeek = deepSeek;
        this.longTermMemory = longTermMemory;
    }

    /**
     * 从一轮对话中提取并持久化长期记忆。
     */
    public void extract(String agentId, String userMessage, String assistantReply) {
        if (userMessage == null || userMessage.length() < MIN_MESSAGE_LENGTH) {
            return;
        }
        if (!hasTriggerKeyword(userMessage)) {
            return;
        }
        String userPart = truncate(userMessage, MAX_INPUT_CHARS);
        String replyPart = truncate(assistantReply == null ? "" : assistantReply, MAX_INPUT_CHARS);

        String userPrompt = "用户消息：\n" + userPart + "\n\n助手回复：\n" + replyPart;

        String json;
        try {
            json = deepSeek.chatJson(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("extract chatJson failed: {}", e.getMessage());
            return;
        }

        JsonNode root = JsonGuard.parseJsonSafe(json);
        if (root == null) {
            log.debug("extract: invalid JSON from LLM: {}", json);
            return;
        }
        JsonNode memories = root.path("memories");
        if (!memories.isArray() || memories.isEmpty()) {
            return;
        }
        for (JsonNode m : memories) {
            String key = JsonGuard.text(m, "key");
            String value = JsonGuard.text(m, "value");
            String category = JsonGuard.text(m, "category");
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            if (category == null || category.isBlank()) {
                category = "note";
            }
            try {
                longTermMemory.save(agentId, key, value, category);
                log.debug("extracted memory: agent={} key={} category={}", agentId, key, category);
            } catch (Exception e) {
                log.warn("save extracted memory failed (key={}): {}", key, e.getMessage());
            }
        }
    }

    /** 触发关键词预过滤。 */
    private boolean hasTriggerKeyword(String message) {
        for (String kw : TRIGGER_KEYWORDS) {
            if (message.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
