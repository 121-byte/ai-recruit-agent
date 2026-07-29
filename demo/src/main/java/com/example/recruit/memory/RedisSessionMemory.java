package com.example.recruit.memory;

import com.example.recruit.config.AppProperties;
import com.example.recruit.infra.llm.DeepSeekModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 短期记忆：Redis 会话历史 (复刻对齐参考 §一-1)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>用 {@code StringRedisTemplate} + Redis <b>List</b> 结构:
 *       {@code opsForList().rightPush(key, "<timestamp>|<role>|<content>")} + {@code expire(TTL)}</li>
 *   <li>方法名: {@code addMessage} (原 appendMessage) / {@code getHistory} (返回 List&lt;String&gt;) /
 *       {@code getRecent(sessionId,n)} / {@code clearSession} / {@code getActiveSessions}</li>
 *   <li>value 格式: {@code <timestamp>|<role>|<content>}，split("\\|",3) 解析</li>
 *   <li>压缩调 chatFast 摘要 prompt 对齐参考，失败回退 trim</li>
 *   <li>保留 ConcurrentHashMap mock 兜底 (标注非参考行为)</li>
 * </ul>
 */
@Component
public class RedisSessionMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionMemory.class);

    private static final String KEY_PREFIX = "agent:session:";
    private static final long DEFAULT_TTL_SECONDS = 3600;       // 1 小时过期
    private static final int MAX_HISTORY = 10;                  // 最多 10 条消息
    private static final int COMPRESS_THRESHOLD = 8;            // 8 条触发压缩
    private static final int KEEP_RECENT_ON_COMPRESS = 4;       // 压缩时保留最近 4 条

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final DeepSeekModelService deepSeek;

    /** Mock / Redis 不可用时的进程内兜底存储 (非参考行为: 演示降级)。 */
    private final ConcurrentHashMap<String, List<String>> fallback = new ConcurrentHashMap<>();

    public RedisSessionMemory(StringRedisTemplate redisTemplate,
                              AppProperties appProperties,
                              DeepSeekModelService deepSeek) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.deepSeek = deepSeek;
    }

    /**
     * 追加一条消息到会话历史: rightPush → 超 MAX_HISTORY 截断 → 超 COMPRESS_THRESHOLD 触发压缩。
     * value 格式: {@code <timestamp>|<role>|<content>}
     */
    public void addMessage(String sessionId, String role, String content) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String entry = ts + "|" + (role == null ? "" : role) + "|" + (content == null ? "" : content);
        if (useMock()) {
            addInMemory(sessionId, entry);
            return;
        }
        try {
            String key = KEY_PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(key, entry);
            redisTemplate.expire(key, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            long size = redisTemplate.opsForList().size(key);
            if (size > MAX_HISTORY) {
                // 截断: 删除超出部分
                long trimFrom = size - MAX_HISTORY;
                redisTemplate.opsForList().trim(key, trimFrom, -1);
                size = MAX_HISTORY;
            }
            if (size >= COMPRESS_THRESHOLD) {
                compressHistory(sessionId, size);
            }
        } catch (Exception e) {
            log.warn("Redis addMessage failed, fallback to in-memory: {}", e.getMessage());
            addInMemory(sessionId, entry);
        }
    }

    /**
     * 读取会话历史，返回 List&lt;String&gt; 原始串 ("timestamp|role|content")。
     */
    public List<String> getHistory(String sessionId) {
        if (useMock()) {
            return new ArrayList<>(fallback.getOrDefault(sessionId, Collections.emptyList()));
        }
        try {
            String key = KEY_PREFIX + sessionId;
            List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
            return raw == null ? Collections.emptyList() : new ArrayList<>(raw);
        } catch (Exception e) {
            log.warn("Redis getHistory failed, fallback to in-memory: {}", e.getMessage());
            return new ArrayList<>(fallback.getOrDefault(sessionId, Collections.emptyList()));
        }
    }

    /** 获取最近 n 条会话历史。 */
    public List<String> getRecent(String sessionId, int n) {
        List<String> all = getHistory(sessionId);
        if (all.size() <= n) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - n, all.size()));
    }

    /** 清除指定会话的短期记忆。 */
    public void clearSession(String sessionId) {
        if (useMock()) {
            fallback.remove(sessionId);
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("Redis clearSession failed: {}", e.getMessage());
            fallback.remove(sessionId);
        }
    }

    /** 获取当前活跃会话 ID 列表 (非参考增强)。 */
    public Set<String> getActiveSessions() {
        if (useMock()) {
            return fallback.keySet();
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null) {
                return Collections.emptySet();
            }
            java.util.Set<String> sessions = new java.util.HashSet<>();
            for (String k : keys) {
                sessions.add(k.substring(KEY_PREFIX.length()));
            }
            return sessions;
        } catch (Exception e) {
            log.warn("Redis getActiveSessions failed: {}", e.getMessage());
            return fallback.keySet();
        }
    }

    // ─────────────────── 压缩 ───────────────────

    /**
     * 渐进式压缩: 将前 (total - 4) 条消息拼接调 LLM 生成摘要，
     * 用 {@code timestamp|summary|[摘要]} 替换旧消息，保留最近 4 条 + 摘要写回。
     * 压缩失败回退 trim。
     */
    public void compressHistory(String sessionId, long currentSize) {
        List<String> hist;
        try {
            String key = KEY_PREFIX + sessionId;
            hist = redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.warn("compressHistory read failed, fallback: {}", e.getMessage());
            hist = new ArrayList<>(fallback.getOrDefault(sessionId, Collections.emptyList()));
        }
        if (hist == null || hist.size() <= KEEP_RECENT_ON_COMPRESS) {
            return;
        }
        List<String> compressed = compressInternal(hist);
        if (useMock()) {
            fallback.put(sessionId, compressed);
            return;
        }
        try {
            String key = KEY_PREFIX + sessionId;
            // 删除旧内容并重建
            redisTemplate.delete(key);
            for (String entry : compressed) {
                redisTemplate.opsForList().rightPush(key, entry);
            }
            redisTemplate.expire(key, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis write compressed failed, fallback to in-memory: {}", e.getMessage());
            fallback.put(sessionId, compressed);
        }
    }

    // ─────────────────── 内部实现 ───────────────────

    private void addInMemory(String sessionId, String entry) {
        List<String> hist = new ArrayList<>(fallback.getOrDefault(sessionId, Collections.emptyList()));
        hist.add(entry);
        if (hist.size() > MAX_HISTORY) {
            hist = new ArrayList<>(hist.subList(hist.size() - MAX_HISTORY, hist.size()));
        }
        if (hist.size() >= COMPRESS_THRESHOLD) {
            hist = compressInternal(hist);
        }
        fallback.put(sessionId, hist);
    }

    private List<String> compressInternal(List<String> hist) {
        int total = hist.size();
        if (total <= KEEP_RECENT_ON_COMPRESS) {
            return hist;
        }
        int toCompress = total - KEEP_RECENT_ON_COMPRESS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toCompress; i++) {
            String[] parts = hist.get(i).split("\\|", 3);
            String role = parts.length > 1 ? parts[1] : "";
            String content = parts.length > 2 ? parts[2] : "";
            sb.append(role).append(": ").append(content).append('\n');
        }
        // 对齐参考 prompt: 用一句话总结对话关键信息
        String summary;
        try {
            summary = deepSeek.chatFast(
                    "用一句话总结对话关键信息，保留：用户意图、关键决策、重要约束。丢弃寒暄和中间过程。",
                    sb.toString());
        } catch (Exception e) {
            log.warn("compress summary failed, use trim fallback: {}", e.getMessage());
            // 失败回退 trim: 保留最近 MAX_HISTORY 条
            int from = Math.max(0, total - MAX_HISTORY);
            return new ArrayList<>(hist.subList(from, total));
        }
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String summaryLine = ts + "|summary|[摘要] " + summary;

        List<String> result = new ArrayList<>();
        result.add(summaryLine);
        result.addAll(hist.subList(total - KEEP_RECENT_ON_COMPRESS, total));
        return result;
    }

    private boolean useMock() {
        return appProperties.useMock() || redisTemplate == null;
    }
}
