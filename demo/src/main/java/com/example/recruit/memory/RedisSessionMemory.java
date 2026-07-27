package com.example.recruit.memory;

import com.example.recruit.config.AppProperties;
import com.example.recruit.llm.DeepSeekModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 短期记忆：Redis 会话历史 (复刻自文档 §5.1)。
 *
 * <p>每个 Agent 的近期对话以 JSON 数组形式存储在 Redis key {@code agent:session:{agentId}}，
 * 最多保留 {@link #MAX_HISTORY} 条；超过 {@link #COMPRESS_THRESHOLD} 条时调用
 * {@link DeepSeekModelService#chatFast} 将较早的消息压缩为一条 {@code [summary]} 摘要。
 *
 * <p>Mock 降级：当 {@link AppProperties#useMock()} 为 true 或 Redis 不可达时，
 * 自动切换到进程内 {@link ConcurrentHashMap} 替代存储，保证不抛异常、不阻断启动。
 */
@Component
public class RedisSessionMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionMemory.class);

    private static final String KEY_PREFIX = "agent:session:";
    private static final long DEFAULT_TTL_SECONDS = 3600;       // 1 小时过期
    private static final int MAX_HISTORY = 10;                  // 最多 10 条消息
    private static final int COMPRESS_THRESHOLD = 8;            // 8 条触发压缩
    private static final int KEEP_RECENT_ON_COMPRESS = 4;       // 压缩时保留最近 4 条

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;
    private final DeepSeekModelService deepSeek;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Mock / Redis 不可用时的进程内兜底存储。 */
    private final ConcurrentHashMap<String, List<Map<String, Object>>> fallback = new ConcurrentHashMap<>();

    public RedisSessionMemory(RedisTemplate<String, String> redisTemplate,
                              AppProperties appProperties,
                              DeepSeekModelService deepSeek) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.deepSeek = deepSeek;
    }

    /**
     * 追加一条消息到会话历史：追加 → 超 MAX_HISTORY 截断 → 超 COMPRESS_THRESHOLD 触发压缩。
     */
    public void appendMessage(String agentId, String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        msg.put("timestamp", LocalDateTime.now().toString());

        if (useMock()) {
            appendInMemory(agentId, msg);
            return;
        }
        try {
            List<Map<String, Object>> hist = readAll(agentId);
            hist.add(msg);
            if (hist.size() > MAX_HISTORY) {
                hist = new ArrayList<>(hist.subList(hist.size() - MAX_HISTORY, hist.size()));
            }
            if (hist.size() >= COMPRESS_THRESHOLD) {
                hist = compressInternal(hist);
            }
            writeAll(agentId, hist);
        } catch (Exception e) {
            log.warn("Redis append failed, fallback to in-memory: {}", e.getMessage());
            appendInMemory(agentId, msg);
        }
    }

    /** 读取会话历史。 */
    public List<Map<String, Object>> getHistory(String agentId) {
        if (useMock()) {
            return new ArrayList<>(fallback.getOrDefault(agentId, Collections.emptyList()));
        }
        try {
            return readAll(agentId);
        } catch (Exception e) {
            log.warn("Redis read failed, fallback to in-memory: {}", e.getMessage());
            return new ArrayList<>(fallback.getOrDefault(agentId, Collections.emptyList()));
        }
    }

    /**
     * 渐进式压缩：将前 (total - 4) 条消息拼接调 LLM 生成摘要，
     * 用 {@code [summary] {摘要}} 替换旧消息，保留最近 4 条 + 摘要写回。
     */
    public void compressHistory(String sessionId, int currentSize) {
        List<Map<String, Object>> hist;
        try {
            hist = readAll(sessionId);
        } catch (Exception e) {
            log.warn("compressHistory read failed, fallback: {}", e.getMessage());
            hist = new ArrayList<>(fallback.getOrDefault(sessionId, Collections.emptyList()));
        }
        if (hist.size() <= KEEP_RECENT_ON_COMPRESS) {
            return;
        }
        List<Map<String, Object>> compressed = compressInternal(hist);
        if (useMock()) {
            fallback.put(sessionId, compressed);
        } else {
            try {
                writeAll(sessionId, compressed);
            } catch (Exception e) {
                log.warn("Redis write compressed failed, fallback to in-memory: {}", e.getMessage());
                fallback.put(sessionId, compressed);
            }
        }
    }

    // ─────────────────── 内部实现 ───────────────────

    private void appendInMemory(String agentId, Map<String, Object> msg) {
        List<Map<String, Object>> hist = new ArrayList<>(fallback.getOrDefault(agentId, Collections.emptyList()));
        hist.add(msg);
        if (hist.size() > MAX_HISTORY) {
            hist = new ArrayList<>(hist.subList(hist.size() - MAX_HISTORY, hist.size()));
        }
        if (hist.size() >= COMPRESS_THRESHOLD) {
            hist = compressInternal(hist);
        }
        fallback.put(agentId, hist);
    }

    private List<Map<String, Object>> compressInternal(List<Map<String, Object>> hist) {
        int total = hist.size();
        if (total <= KEEP_RECENT_ON_COMPRESS) {
            return hist;
        }
        int toCompress = total - KEEP_RECENT_ON_COMPRESS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toCompress; i++) {
            Map<String, Object> m = hist.get(i);
            sb.append(m.getOrDefault("role", "")).append(": ")
                    .append(m.getOrDefault("content", "")).append('\n');
        }
        String summary;
        try {
            summary = deepSeek.chatFast(
                    "你是会话摘要助手。将多轮对话压缩为简洁中文摘要，保留关键事实、用户偏好与决策。",
                    sb.toString());
        } catch (Exception e) {
            log.warn("compress summary failed, use raw concat: {}", e.getMessage());
            summary = sb.toString();
        }
        Map<String, Object> summaryMsg = new LinkedHashMap<>();
        summaryMsg.put("role", "system");
        summaryMsg.put("content", "[summary] " + summary);
        summaryMsg.put("timestamp", LocalDateTime.now().toString());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(summaryMsg);
        result.addAll(hist.subList(total - KEEP_RECENT_ON_COMPRESS, total));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAll(String agentId) throws Exception {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + agentId);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> list = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        return new ArrayList<>(list);
    }

    private void writeAll(String agentId, List<Map<String, Object>> hist) throws Exception {
        String key = KEY_PREFIX + agentId;
        String json = mapper.writeValueAsString(hist);
        redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private boolean useMock() {
        return appProperties.useMock() || redisTemplate == null;
    }
}
