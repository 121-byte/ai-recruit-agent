package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.infra.retrieval.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 长期记忆：封装 {@link MemoryEntryMapper} 的 CRUD + 向量/关键词检索
 * (复刻对齐参考 §一-2)。
 *
 * <p>方法契约对齐参考：
 * <ul>
 *   <li>{@code store} — 直接 insert (不去重)</li>
 *   <li>{@code upsert} — 先 findByAgentIdAndKey，存在 update 不存在 store</li>
 *   <li>{@code get(agentId, key) → Optional<MemoryEntry>}</li>
 *   <li>{@code delete(agentId, key)}</li>
 *   <li>{@code search(agentId, query)} — 内部 searchByKeyword("%"+query+"%")</li>
 * </ul>
 *
 * <p>embedding 内容对齐参考：{@code embed(key + ": " + value)} 使记忆键参与向量化。
 *
 * <p>{@code save} 保留转发 upsert，兼容旧调用方。
 * {@code searchByVector} 保留仅供测试 (主路径走 HybridMemoryRetriever 的 JdbcTemplate)。
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

    // ─────────────────── 对齐参考: store / upsert / get / delete / search ───────────────────

    /**
     * 直接 insert 一条记忆 (不去重，对齐参考 store)。
     */
    @Transactional
    public void store(String agentId, String key, String value, String category) {
        float[] emb = embeddingService.embed(key + ": " + value);
        LocalDateTime now = LocalDateTime.now();
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
    }

    /**
     * 先 findByAgentIdAndKey，存在 update 不存在 store (对齐参考 upsert)。
     */
    @Transactional
    public void upsert(String agentId, String key, String value, String category) {
        MemoryEntry existing = findByAgentIdAndKey(agentId, key);
        if (existing != null) {
            float[] emb = embeddingService.embed(key + ": " + value);
            existing.setMemoryValue(value);
            existing.setCategory(category);
            existing.setEmbedding(emb);
            existing.setUpdatedAt(LocalDateTime.now());
            memoryEntryMapper.updateById(existing);
            return;
        }
        store(agentId, key, value, category);
    }

    /**
     * 按 (agentId, key) 查询单条记忆，返回 Optional (对齐参考 get)。
     */
    public Optional<MemoryEntry> get(String agentId, String key) {
        MemoryEntry entry = findByAgentIdAndKey(agentId, key);
        return Optional.ofNullable(entry);
    }

    /**
     * 按 (agentId, key) 删除记忆 (对齐参考 delete)。
     */
    @Transactional
    public void delete(String agentId, String key) {
        memoryEntryMapper.deleteByAgentIdAndKey(agentId, key);
    }

    /**
     * 关键词检索 (对齐参考 search)：内部调 searchByKeyword("%"+query+"%")。
     */
    public List<MemoryEntry> search(String agentId, String query) {
        return searchByKeyword(agentId, "%" + (query == null ? "" : query) + "%");
    }

    // ─────────────────── 兼容旧调用方 ───────────────────

    /**
     * 转发 upsert，兼容旧调用方 (M1 前代码用 save)。
     */
    public void save(String agentId, String key, String value, String category) {
        try {
            upsert(agentId, key, value, category);
        } catch (Exception e) {
            log.warn("save(upsert) memory failed (agent={}, key={}): {}", agentId, key, e.getMessage());
        }
    }

    // ─────────────────── 检索方法 ───────────────────

    /** pgvector 向量检索：cosine distance 排序 top-K (仅供测试; 主路径走 HybridMemoryRetriever JdbcTemplate)。 */
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

    // ─────────────────── 内部工具 ───────────────────

    /** 按 (agentId, key) 查询单条。 */
    private MemoryEntry findByAgentIdAndKey(String agentId, String key) {
        try {
            return memoryEntryMapper.selectOne(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .eq(MemoryEntry::getAgentId, agentId)
                            .eq(MemoryEntry::getMemoryKey, key));
        } catch (Exception e) {
            log.debug("findByAgentIdAndKey failed (agent={}, key={}): {}", agentId, key, e.getMessage());
            return null;
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
