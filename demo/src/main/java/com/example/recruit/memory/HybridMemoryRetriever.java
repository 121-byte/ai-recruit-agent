package com.example.recruit.memory;

import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.llm.EmbeddingService;
import com.example.recruit.llm.RerankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索：向量 + 关键词 + 图谱游走，RRF 融合后按 时间衰减 × 重要性 加权
 * (复刻对齐参考 §一-5)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>引入 JdbcTemplate + RerankService</li>
 *   <li>vectorSearch 改 JdbcTemplate 原生 SQL: 1-(embedding&lt;=&gt;?) AS similarity,
 *       WHERE category!='archived' AND embedding IS NOT NULL, LIMIT 10; rawScore=DB similarity</li>
 *   <li>keywordSearch 过滤 archived; rawScore=0.5</li>
 *   <li>ScoredMemory 加 source 字段 (vector/keyword/graph)</li>
 *   <li>graphWalk 改 batch UNION SQL (正反向一次查)</li>
 *   <li>recencyFactor 优先 lastAccess 回退 updatedAt</li>
 *   <li>importanceFactor ≥0.7→1.5/≤0.3→0.5/else 1.0</li>
 *   <li>RRF k=60; 记忆&gt;5 时调 rerankService.rerank(query, memoryTexts, 5) 取 Top5</li>
 *   <li>缓存 key = agentId + ":" + query</li>
 * </ul>
 */
@Component
public class HybridMemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridMemoryRetriever.class);

    private static final int TOP_K = 10;
    private static final double RECENCY_HALF_LIFE_DAYS = 30.0;
    private static final int RRF_K = 60;
    private static final int RERANK_TOP = 5;

    /** 命中记录：cacheKey = agentId + ":" + query。 */
    private static final ThreadLocal<Map<String, List<ScoredMemory>>> CACHE =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private final MemoryEntryMapper memoryEntryMapper;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final RerankService rerankService;

    public HybridMemoryRetriever(MemoryEntryMapper memoryEntryMapper,
                                 EmbeddingService embeddingService,
                                 JdbcTemplate jdbcTemplate,
                                 RerankService rerankService) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.rerankService = rerankService;
    }

    /**
     * 检索结果记录。字段公开以便融合算法直接读写。
     */
    public static class ScoredMemory {
        public MemoryEntry entry;
        public double rawScore;
        public double rrfScore;
        public double finalScore;
        public String source;  // vector/keyword/graph

        public ScoredMemory(MemoryEntry entry, double rawScore, String source) {
            this.entry = entry;
            this.rawScore = rawScore;
            this.rrfScore = 0.0;
            this.finalScore = 0.0;
            this.source = source;
        }
    }

    /**
     * 混合检索主入口。
     */
    public List<ScoredMemory> retrieve(String agentId, String query) {
        String cacheKey = agentId + ":" + query;
        List<ScoredMemory> cached = CACHE.get().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        float[] queryVector;
        try {
            queryVector = embeddingService.embed(query);
        } catch (Exception e) {
            log.debug("embed query failed: {}", e.getMessage());
            return Collections.emptyList();
        }

        // 三路检索
        List<ScoredMemory> vectorResults = vectorSearch(agentId, query, queryVector);
        List<ScoredMemory> keywordResults = keywordSearch(agentId, query);
        List<ScoredMemory> graphResults = graphWalk(agentId, vectorResults, keywordResults);

        // RRF 融合
        Map<Long, ScoredMemory> merged = rrfFusion(vectorResults, keywordResults, graphResults);

        // 时间衰减 × 重要性加权
        for (ScoredMemory sm : merged.values()) {
            double recency = recencyFactor(sm.entry);
            double importance = importanceFactor(sm.entry);
            sm.finalScore = sm.rrfScore * recency * importance;
        }
        List<ScoredMemory> ranked = new ArrayList<>(merged.values());
        ranked.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        List<ScoredMemory> result = ranked.size() > TOP_K
                ? new ArrayList<>(ranked.subList(0, TOP_K)) : ranked;

        // Rerank: 记忆 >5 时调 rerankService.rerank(query, memoryTexts, 5) 取 Top5
        if (result.size() > RERANK_TOP) {
            try {
                List<String> texts = result.stream()
                        .map(sm -> sm.entry.getMemoryKey() + ": " + sm.entry.getMemoryValue())
                        .collect(Collectors.toList());
                List<Integer> rerankIndices = rerankService.rerank(query, texts, RERANK_TOP);
                List<ScoredMemory> reranked = new ArrayList<>();
                for (int idx : rerankIndices) {
                    if (idx >= 0 && idx < result.size()) {
                        reranked.add(result.get(idx));
                    }
                }
                log.debug("Memory rerank: {} → {}", result.size(), reranked.size());
                result = reranked;
            } catch (Exception e) {
                log.debug("Memory rerank failed, using RRF result: {}", e.getMessage());
                result = result.subList(0, Math.min(RERANK_TOP, result.size()));
            }
        }

        CACHE.get().put(cacheKey, result);
        return result;
    }

    /** 清理 ThreadLocal 缓存：每轮对话结束时调用。 */
    public static void clearCache() {
        CACHE.remove();
    }

    // ─────────────────── 三路检索 ───────────────────

    /**
     * 向量检索: JdbcTemplate 原生 SQL, 1-(embedding&lt;=&gt;?) AS similarity,
     * WHERE category!='archived' AND embedding IS NOT NULL, LIMIT 10; rawScore=similarity。
     */
    private List<ScoredMemory> vectorSearch(String agentId, String query, float[] queryVector) {
        String literal = FloatVectorTypeHandler.literal(queryVector);
        String sql = "SELECT id, agent_id, memory_key, memory_value, category, tags, access_count, " +
                "last_access, importance, embedding, created_at, updated_at, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM memory_entry WHERE agent_id = ? AND category != 'archived' " +
                "AND embedding IS NOT NULL ORDER BY embedding <=> ?::vector LIMIT 10";
        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> mapScoredMemory(rs, "vector"),
                    literal, agentId, literal);
        } catch (Exception e) {
            log.debug("vectorSearch failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 关键词检索: 过滤 archived; rawScore=0.5。 */
    private List<ScoredMemory> keywordSearch(String agentId, String query) {
        try {
            List<MemoryEntry> entries = memoryEntryMapper.searchByKeyword(agentId, query);
            List<ScoredMemory> result = new ArrayList<>();
            for (MemoryEntry e : entries) {
                if (e.getCategory() != null && "archived".equals(e.getCategory())) {
                    continue;  // 过滤 archived
                }
                result.add(new ScoredMemory(e, 0.5, "keyword"));
            }
            return result;
        } catch (Exception e) {
            log.debug("keywordSearch failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 图谱游走: batch UNION SQL (正反向一次查), rawScore=edge weight。
     * 过滤非本人/已归档。
     */
    private List<ScoredMemory> graphWalk(String agentId,
                                         List<ScoredMemory> vectorResults,
                                         List<ScoredMemory> keywordResults) {
        Set<Long> seeds = new HashSet<>();
        vectorResults.forEach(sm -> { if (sm.entry.getId() != null) seeds.add(sm.entry.getId()); });
        keywordResults.forEach(sm -> { if (sm.entry.getId() != null) seeds.add(sm.entry.getId()); });
        if (seeds.isEmpty()) {
            return Collections.emptyList();
        }

        // 构造 IN 参数: 用占位符
        String placeholders = seeds.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] seedParams = seeds.toArray();
        // UNION batch: 正反向一次查
        String sql = "SELECT target_entry_id AS related_id, weight FROM memory_graph " +
                "WHERE source_entry_id IN (" + placeholders + ") AND agent_id = ? " +
                "UNION " +
                "SELECT source_entry_id AS related_id, weight FROM memory_graph " +
                "WHERE target_entry_id IN (" + placeholders + ") AND agent_id = ?";

        // 参数: seeds + agentId + seeds + agentId
        Object[] params = new Object[seedParams.length * 2 + 2];
        System.arraycopy(seedParams, 0, params, 0, seedParams.length);
        params[seedParams.length] = agentId;
        System.arraycopy(seedParams, 0, params, seedParams.length + 1, seedParams.length);
        params[seedParams.length * 2 + 1] = agentId;

        Map<Long, Double> relatedWeights = new HashMap<>();
        try {
            jdbcTemplate.query(sql, rs -> {
                long relatedId = rs.getLong("related_id");
                double weight = rs.getDouble("weight");
                if (!rs.wasNull() && relatedWeights.getOrDefault(relatedId, 0.0) < weight) {
                    relatedWeights.put(relatedId, weight);
                }
            }, params);
        } catch (Exception e) {
            log.debug("graphWalk failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }

        if (relatedWeights.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载关联记忆, 过滤非本人/已归档
        List<MemoryEntry> related = memoryEntryMapper.selectBatchIds(relatedWeights.keySet());
        List<ScoredMemory> result = new ArrayList<>();
        for (MemoryEntry e : related) {
            if (e == null || e.getId() == null) continue;
            // 过滤非本人
            if (!agentId.equals(e.getAgentId())) continue;
            // 过滤已归档
            if ("archived".equals(e.getCategory())) continue;
            // 过滤已在 seed 中的
            if (seeds.contains(e.getId())) continue;
            double w = relatedWeights.getOrDefault(e.getId(), 0.5);
            result.add(new ScoredMemory(e, w, "graph"));
        }
        return result;
    }

    // ─────────────────── 融合与加权 ───────────────────

    /**
     * RRF 融合: score = Σ 1/(k + rank + 1)，k=60。
     */
    @SafeVarargs
    private final Map<Long, ScoredMemory> rrfFusion(List<ScoredMemory>... resultLists) {
        Map<Long, ScoredMemory> merged = new HashMap<>();
        for (List<ScoredMemory> list : resultLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                ScoredMemory sm = list.get(rank);
                if (sm.entry.getId() == null) continue;
                ScoredMemory existing = merged.get(sm.entry.getId());
                if (existing == null) {
                    sm.rrfScore = 1.0 / (RRF_K + rank + 1);
                    merged.put(sm.entry.getId(), sm);
                } else {
                    existing.rrfScore += 1.0 / (RRF_K + rank + 1);
                }
            }
        }
        return merged;
    }

    /**
     * 时间衰减: 优先 lastAccess, 回退 updatedAt; factor = exp(-days/30.0)。
     */
    private double recencyFactor(MemoryEntry entry) {
        LocalDateTime ref = entry.getLastAccess() != null ? entry.getLastAccess() : entry.getUpdatedAt();
        if (ref == null) {
            return 1.0;
        }
        long days = Duration.between(ref, LocalDateTime.now()).toDays();
        return Math.exp(-days / RECENCY_HALF_LIFE_DAYS);
    }

    /** 重要性加权: ≥0.7→1.5，≤0.3→0.5，否则 1.0；null 视为 0.5。 */
    private double importanceFactor(MemoryEntry entry) {
        Double imp = entry.getImportance();
        double v = (imp == null) ? 0.5 : imp;
        if (v >= 0.7) {
            return 1.5;
        }
        if (v <= 0.3) {
            return 0.5;
        }
        return 1.0;
    }

    // ─────────────────── 内部工具 ───────────────────

    /** JdbcTemplate mapRow: 从 ResultSet 构造 ScoredMemory (vector 源, rawScore=similarity)。 */
    private ScoredMemory mapScoredMemory(ResultSet rs, String source) throws SQLException {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(rs.getLong("id"));
        entry.setAgentId(rs.getString("agent_id"));
        entry.setMemoryKey(rs.getString("memory_key"));
        entry.setMemoryValue(rs.getString("memory_value"));
        entry.setCategory(rs.getString("category"));
        entry.setAccessCount(rs.getInt("access_count"));
        entry.setLastAccess(rs.getTimestamp("last_access") != null
                ? rs.getTimestamp("last_access").toLocalDateTime() : null);
        double imp = rs.getDouble("importance");
        entry.setImportance(rs.wasNull() ? null : imp);
        entry.setCreatedAt(rs.getTimestamp("created_at") != null
                ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        entry.setUpdatedAt(rs.getTimestamp("updated_at") != null
                ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        double similarity = rs.getDouble("similarity");
        return new ScoredMemory(entry, similarity, source);
    }
}
