package com.example.recruit.service;

import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.memory.PostgresLongTermMemory;
import com.example.recruit.memory.RedisSessionMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 记忆门面服务 (复刻对齐清单 §4.7)。
 *
 * <p>聚合长期记忆 (PostgresLongTermMemory) 与短期记忆 (RedisSessionMemory)，
 * 对外提供统一的 save / search / getPreferences / appendShortTerm / getShortTerm 接口。
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final PostgresLongTermMemory longTermMemory;
    private final RedisSessionMemory sessionMemory;
    private final EmbeddingService embeddingService;

    public MemoryService(PostgresLongTermMemory longTermMemory,
                         RedisSessionMemory sessionMemory,
                         EmbeddingService embeddingService) {
        this.longTermMemory = longTermMemory;
        this.sessionMemory = sessionMemory;
        this.embeddingService = embeddingService;
    }

    /** 保存长期记忆。 */
    public void save(String agentId, String key, String value, String category) {
        try {
            longTermMemory.save(agentId, key, value, category);
        } catch (Exception e) {
            log.warn("save memory failed: {}", e.getMessage());
        }
    }

    /** 向量检索长期记忆，返回 topK 条。query 文本先经 embedding 向量化。 */
    public List<MemoryEntry> search(String agentId, String query, int topK) {
        try {
            float[] vec = embeddingService.embed(query);
            return longTermMemory.searchByVector(agentId, vec, topK);
        } catch (Exception e) {
            log.warn("search memory failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 获取某 agent 的偏好类记忆（category = preference）。 */
    public List<MemoryEntry> getPreferences(String agentId) {
        try {
            return longTermMemory.getByCategory(agentId, "preference");
        } catch (Exception e) {
            log.warn("getPreferences failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 追加一条短期记忆（会话历史）。 */
    public void appendShortTerm(String agentId, String role, String content) {
        try {
            sessionMemory.appendMessage(agentId, role, content);
        } catch (Exception e) {
            log.warn("appendShortTerm failed: {}", e.getMessage());
        }
    }

    /** 获取短期记忆（会话历史）。 */
    public List<Map<String, Object>> getShortTerm(String agentId) {
        try {
            return sessionMemory.getHistory(agentId);
        } catch (Exception e) {
            log.warn("getShortTerm failed: {}", e.getMessage());
            return List.of();
        }
    }
}
