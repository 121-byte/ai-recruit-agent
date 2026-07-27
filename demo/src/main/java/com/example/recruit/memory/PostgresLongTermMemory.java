package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.llm.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 长期记忆：封装 {@link MemoryEntryMapper} 的 CRUD + 向量/关键词检索
 * (复刻自文档 §5.2)。
 *
 * <p>save 利用 UNIQUE(agent_id, memory_key) 约束语义实现 upsert：
 * 先按 (agentId, memoryKey) 查询，存在则更新 value/category/embedding，否则插入新条目。
 *
 * <p>所有数据库调用 try/catch，失败静默返回空，保证 Mock / H2 降级环境下不阻断调用方。
 */
@Component
public class PostgresLongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(PostgresLongTermMemory.class);

    private final MemoryEntryMapper memoryEntryMapper;
    private final EmbeddingService embeddingService;
    private final AppProperties appProperties;

    public PostgresLongTermMemory(MemoryEntryMapper memoryEntryMapper,
                                  EmbeddingService embeddingService,
                                  AppProperties appProperties) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.embeddingService = embeddingService;
        this.appProperties = appProperties;
    }

    /**
     * 插入或更新记忆。UNIQUE(agent_id, memory_key) 去重：
     * 存在 → 更新 value/category/embedding；不存在 → 插入。
     */
    public void save(String agentId, String key, String value, String category) {
        try {
            float[] emb = embeddingService.embed(value);
            LocalDateTime now = LocalDateTime.now();

            MemoryEntry existing = memoryEntryMapper.selectOne(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .eq(MemoryEntry::getAgentId, agentId)
                            .eq(MemoryEntry::getMemoryKey, key));
            if (existing != null) {
                existing.setMemoryValue(value);
                existing.setCategory(category);
                existing.setEmbedding(emb);
                existing.setUpdatedAt(now);
                memoryEntryMapper.updateById(existing);
                return;
            }
            MemoryEntry entry = new MemoryEntry();
            entry.setAgentId(agentId);
            entry.setMemoryKey(key);
            entry.setMemoryValue(value);
            entry.setCategory(category);
            entry.setTags(new String[0]);
            entry.setAccessCount(0);
            entry.setLastAccess(now);
            entry.setImportance(0.5);
            entry.setEmbedding(emb);
            entry.setCreatedAt(now);
            entry.setUpdatedAt(now);
            memoryEntryMapper.insert(entry);
        } catch (Exception e) {
            log.warn("save memory failed (agent={}, key={}): {}", agentId, key, e.getMessage());
        }
    }

    /** pgvector 向量检索：cosine distance 排序 top-K。 */
    public List<MemoryEntry> searchByVector(String agentId, float[] queryVector, int topK) {
        try {
            return memoryEntryMapper.searchByVector(
                    agentId, FloatVectorTypeHandler.literal(queryVector), topK);
        } catch (Exception e) {
            log.debug("searchByVector failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** pg_trgm / ILIKE 关键词模糊检索。 */
    public List<MemoryEntry> searchByKeyword(String agentId, String keyword) {
        try {
            return memoryEntryMapper.searchByKeyword(agentId, keyword);
        } catch (Exception e) {
            log.debug("searchByKeyword failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 按类别查询 (preference/fact/note/archived)。 */
    public List<MemoryEntry> getByCategory(String agentId, String category) {
        try {
            return memoryEntryMapper.selectList(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .eq(MemoryEntry::getAgentId, agentId)
                            .eq(MemoryEntry::getCategory, category));
        } catch (Exception e) {
            log.debug("getByCategory failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 查询某 Agent 的全部记忆。 */
    public List<MemoryEntry> getAll(String agentId) {
        try {
            return memoryEntryMapper.selectList(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .eq(MemoryEntry::getAgentId, agentId));
        } catch (Exception e) {
            log.debug("getAll failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 按 id 加载单条记忆。 */
    public MemoryEntry getById(Long id) {
        try {
            return memoryEntryMapper.selectById(id);
        } catch (Exception e) {
            log.debug("getById failed (id={}): {}", id, e.getMessage());
            return null;
        }
    }

    /** 批量按 id 加载。 */
    public List<MemoryEntry> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return memoryEntryMapper.selectBatchIds(ids);
        } catch (Exception e) {
            log.debug("getByIds failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 更新重要性分数。 */
    public void updateImportance(Long id, double importance) {
        try {
            MemoryEntry entry = memoryEntryMapper.selectById(id);
            if (entry != null) {
                entry.setImportance(importance);
                entry.setUpdatedAt(LocalDateTime.now());
                memoryEntryMapper.updateById(entry);
            }
        } catch (Exception e) {
            log.warn("updateImportance failed (id={}): {}", id, e.getMessage());
        }
    }

    /**
     * 可见性：是否处于 Mock 降级模式。
     * 供 {@link HybridMemoryRetriever} 等上游判断是否短路返回空。
     */
    public boolean useMock() {
        return appProperties.useMock();
    }
}
