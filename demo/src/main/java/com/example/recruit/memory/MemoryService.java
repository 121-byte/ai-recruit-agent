package com.example.recruit.memory;

import com.example.recruit.dal.entity.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 记忆门面服务 (复刻对齐参考 §一-3)。
 *
 * <p>包归属对齐参考: {@code com.example.recruit.memory} (非 service/)。
 * 构造器 {@code (RedisSessionMemory, PostgresLongTermMemory)} (无 EmbeddingService 依赖)。
 *
 * <p>方法名全量对齐参考：
 * <ul>
 *   <li>短期：addToSession / getSessionHistory / getRecentSession / clearSession</li>
 *   <li>长期：storeLongTerm / upsertLongTerm / getLongTerm→Optional / getLongTermByCategory /
 *       getAllLongTerm / deleteLongTerm / searchLongTerm</li>
 * </ul>
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final PostgresLongTermMemory longTermMemory;
    private final RedisSessionMemory sessionMemory;

    public MemoryService(PostgresLongTermMemory longTermMemory,
                         RedisSessionMemory sessionMemory) {
        this.longTermMemory = longTermMemory;
        this.sessionMemory = sessionMemory;
    }

    // ─────────────────── 短期记忆 ───────────────────

    /** 追加一条短期记忆 (会话历史)。 */
    public void addToSession(String agentId, String role, String content) {
        try {
            sessionMemory.addMessage(agentId, role, content);
        } catch (Exception e) {
            log.warn("addToSession failed: {}", e.getMessage());
        }
    }

    /** 获取短期记忆 (会话历史)，返回原始 "timestamp|role|content" 字符串列表。 */
    public List<String> getSessionHistory(String agentId) {
        try {
            return sessionMemory.getHistory(agentId);
        } catch (Exception e) {
            log.warn("getSessionHistory failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 获取最近 n 条会话历史。 */
    public List<String> getRecentSession(String sessionId, int n) {
        try {
            return sessionMemory.getRecent(sessionId, n);
        } catch (Exception e) {
            log.warn("getRecentSession failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 清除指定会话的短期记忆。 */
    public void clearSession(String sessionId) {
        try {
            sessionMemory.clearSession(sessionId);
        } catch (Exception e) {
            log.warn("clearSession failed: {}", e.getMessage());
        }
    }

    // ─────────────────── 长期记忆 ───────────────────

    /** 保存长期记忆 (store，直接 insert 不去重)。 */
    public void storeLongTerm(String agentId, String key, String value, String category) {
        try {
            longTermMemory.store(agentId, key, value, category);
        } catch (Exception e) {
            log.warn("storeLongTerm failed: {}", e.getMessage());
        }
    }

    /** upsert 长期记忆 (存在 update 不存在 store)。 */
    public void upsertLongTerm(String agentId, String key, String value, String category) {
        try {
            longTermMemory.upsert(agentId, key, value, category);
        } catch (Exception e) {
            log.warn("upsertLongTerm failed: {}", e.getMessage());
        }
    }

    /** 按 (agentId, key) 获取长期记忆，返回 Optional。 */
    public Optional<MemoryEntry> getLongTerm(String agentId, String key) {
        try {
            return longTermMemory.get(agentId, key);
        } catch (Exception e) {
            log.warn("getLongTerm failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 按 category 获取长期记忆。 */
    public List<MemoryEntry> getLongTermByCategory(String agentId, String category) {
        try {
            return longTermMemory.getByCategory(agentId, category);
        } catch (Exception e) {
            log.warn("getLongTermByCategory failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 获取某 agent 的全部长期记忆。 */
    public List<MemoryEntry> getAllLongTerm(String agentId) {
        try {
            return longTermMemory.getAll(agentId);
        } catch (Exception e) {
            log.warn("getAllLongTerm failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按 (agentId, key) 删除长期记忆。 */
    public void deleteLongTerm(String agentId, String key) {
        try {
            longTermMemory.delete(agentId, key);
        } catch (Exception e) {
            log.warn("deleteLongTerm failed: {}", e.getMessage());
        }
    }

    /** 关键词检索长期记忆 (无 topK 参数，内部走 keyword)。 */
    public List<MemoryEntry> searchLongTerm(String agentId, String query) {
        try {
            return longTermMemory.search(agentId, query);
        } catch (Exception e) {
            log.warn("searchLongTerm failed: {}", e.getMessage());
            return List.of();
        }
    }
}
