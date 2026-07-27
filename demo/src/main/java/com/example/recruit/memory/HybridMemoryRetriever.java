package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.entity.MemoryGraph;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.dal.mapper.MemoryGraphMapper;
import com.example.recruit.llm.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索：向量 + 关键词 + 图谱游走，RRF 融合后按 时间衰减 × 重要性 加权
 * (复刻自文档 §5.3，276 行)。
 *
 * <p>算法步骤：
 * <ol>
 *   <li>ThreadLocal 缓存：cacheKey = agentId + "|" + query，命中直接返回
 *       (避免 ReAct 迭代中重复 embedding API 调用)</li>
 *   <li>向量检索：embed(query) → cosine distance top-10，rawScore = cosine 相似度</li>
 *   <li>关键词检索：ILIKE 模糊匹配，rawScore = 1.0</li>
 *   <li>图谱游走：以向量+关键词结果 id 为 seed，正反向查 memory_graph，rawScore = edge weight</li>
 *   <li>RRF 融合：score = Σ 1/(k + rank + 1)，k=60</li>
 *   <li>时间衰减：factor = exp(-days / 30.0)，30 天半衰期</li>
 *   <li>重要性加权：≥0.7→1.5，≤0.3→0.5，否则 1.0</li>
 *   <li>最终排序：finalScore = rrfScore × recencyFactor × importanceFactor，降序取 top-10</li>
 * </ol>
 *
 * <p>所有 DB 调用 try/catch，失败返回空 list，绝不抛异常 (Mock 模式返回空)。
 */
@Component
public class HybridMemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridMemoryRetriever.class);

    private static final int TOP_K = 10;
    private static final double RECENCY_HALF_LIFE_DAYS = 30.0; // 30 天半衰期
    private static final int RRF_K = 60;

    /** 命中记录：cacheKey = agentId + "|" + query，避免 ReAct 迭代重复 embed。 */
    private static final ThreadLocal<Map<String, List<ScoredMemory>>> CACHE =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private final PostgresLongTermMemory longTermMemory;
    private final EmbeddingService embeddingService;
    private final AppProperties appProperties;
    private final MemoryEntryMapper memoryEntryMapper;
    private final MemoryGraphMapper memoryGraphMapper;

    public HybridMemoryRetriever(PostgresLongTermMemory longTermMemory,
                                 EmbeddingService embeddingService,
                                 AppProperties appProperties,
                                 MemoryEntryMapper memoryEntryMapper,
                                 MemoryGraphMapper memoryGraphMapper) {
        this.longTermMemory = longTermMemory;
        this.embeddingService = embeddingService;
        this.appProperties = appProperties;
        this.memoryEntryMapper = memoryEntryMapper;
        this.memoryGraphMapper = memoryGraphMapper;
    }

    /**
     * 检索结果记录。字段公开以便融合算法直接读写。
     */
    public static class ScoredMemory {
        public MemoryEntry entry;
        public double rawScore;
        public double rrfScore;
        public double finalScore;

        public ScoredMemory(MemoryEntry entry, double rawScore, double rrfScore, double finalScore) {
            this.entry = entry;
            this.rawScore = rawScore;
            this.rrfScore = rrfScore;
            this.finalScore = finalScore;
        }

        public ScoredMemory(MemoryEntry entry, double rawScore) {
            this(entry, rawScore, 0.0, 0.0);
        }
    }

    /**
     * 混合检索主入口。
     */
    public List<ScoredMemory> retrieve(String agentId, String query) {
        if (appProperties.useMock()) {
            return Collections.emptyList();
        }
        String cacheKey = agentId + "|" + query;
        List<ScoredMemory> cached = CACHE.get().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 步骤 2-4：三路检索
        float[] queryVector;
        try {
            queryVector = embeddingService.embed(query);
        } catch (Exception e) {
            log.debug("embed query failed: {}", e.getMessage());
            return Collections.emptyList();
        }

        List<ScoredMemory> vectorResults = vectorSearch(agentId, query, queryVector);
        List<ScoredMemory> keywordResults = keywordSearch(agentId, query);
        List<ScoredMemory> graphResults = graphWalk(agentId, vectorResults, keywordResults);

        // 步骤 5：RRF 融合
        Map<Long, ScoredMemory> merged = rrfFusion(vectorResults, keywordResults, graphResults);

        // 步骤 6-8：时间衰减 × 重要性加权，降序取 top-10
        for (ScoredMemory sm : merged.values()) {
            double recency = recencyFactor(sm.entry);
            double importance = importanceFactor(sm.entry);
            sm.finalScore = sm.rrfScore * recency * importance;
        }
        List<ScoredMemory> ranked = new ArrayList<>(merged.values());
        ranked.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        List<ScoredMemory> result = ranked.size() > TOP_K
                ? new ArrayList<>(ranked.subList(0, TOP_K)) : ranked;

        // 步骤 9：写 ThreadLocal 缓存
        CACHE.get().put(cacheKey, result);
        return result;
    }

    /** 清理 ThreadLocal 缓存：每轮对话结束时调用。 */
    public static void clearCache() {
        CACHE.remove();
    }

    // ─────────────────── 三路检索 ───────────────────

    /** 向量检索：cosine distance top-10，rawScore = cosine 相似度。 */
    private List<ScoredMemory> vectorSearch(String agentId, String query, float[] queryVector) {
        List<MemoryEntry> entries = longTermMemory.searchByVector(agentId, queryVector, TOP_K);
        List<ScoredMemory> result = new ArrayList<>();
        for (MemoryEntry e : entries) {
            double raw = (e.getEmbedding() == null) ? 0.0
                    : FloatVectorTypeHandler.cosine(queryVector, e.getEmbedding());
            result.add(new ScoredMemory(e, raw));
        }
        return result;
    }

    /** 关键词检索：ILIKE 模糊匹配，rawScore = 1.0。 */
    private List<ScoredMemory> keywordSearch(String agentId, String query) {
        List<MemoryEntry> entries = longTermMemory.searchByKeyword(agentId, query);
        List<ScoredMemory> result = new ArrayList<>();
        for (MemoryEntry e : entries) {
            result.add(new ScoredMemory(e, 1.0));
        }
        return result;
    }

    /**
     * 图谱游走：以向量+关键词结果的 memory id 为 seed，
     * 查 memory_graph 的 target_entry_id (正向) 与 source_entry_id (反向)，
     * 加载关联记忆，rawScore = edge weight。
     */
    private List<ScoredMemory> graphWalk(String agentId,
                                         List<ScoredMemory> vectorResults,
                                         List<ScoredMemory> keywordResults) {
        Set<Long> seeds = new java.util.HashSet<>();
        vectorResults.forEach(sm -> seeds.add(sm.entry.getId()));
        keywordResults.forEach(sm -> seeds.add(sm.entry.getId()));
        if (seeds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ScoredMemory> collected = new HashMap<>();
        try {
            // 正向：source_entry_id IN seeds → target_entry_id
            List<MemoryGraph> forward = memoryGraphMapper.selectList(
                    new LambdaQueryWrapper<MemoryGraph>()
                            .eq(MemoryGraph::getAgentId, agentId)
                            .in(MemoryGraph::getSourceEntryId, seeds));
            for (MemoryGraph edge : forward) {
                Long targetId = edge.getTargetEntryId();
                if (seeds.contains(targetId) || collected.containsKey(targetId)) {
                    continue;
                }
                MemoryEntry rel = memoryEntryMapper.selectById(targetId);
                if (rel != null) {
                    double w = edge.getWeight() == null ? 0.5 : edge.getWeight();
                    collected.put(targetId, new ScoredMemory(rel, w));
                }
            }
            // 反向：target_entry_id IN seeds → source_entry_id
            List<MemoryGraph> reverse = memoryGraphMapper.selectList(
                    new LambdaQueryWrapper<MemoryGraph>()
                            .eq(MemoryGraph::getAgentId, agentId)
                            .in(MemoryGraph::getTargetEntryId, seeds));
            for (MemoryGraph edge : reverse) {
                Long sourceId = edge.getSourceEntryId();
                if (seeds.contains(sourceId) || collected.containsKey(sourceId)) {
                    continue;
                }
                MemoryEntry rel = memoryEntryMapper.selectById(sourceId);
                if (rel != null) {
                    double w = edge.getWeight() == null ? 0.5 : edge.getWeight();
                    collected.put(sourceId, new ScoredMemory(rel, w));
                }
            }
        } catch (Exception e) {
            log.debug("graphWalk failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
        return new ArrayList<>(collected.values());
    }

    // ─────────────────── 融合与加权 ───────────────────

    /**
     * RRF 融合：score = Σ 1/(k + rank + 1)，k=60。
     * 不依赖原始分数量纲，只用排名。
     */
    private Map<Long, ScoredMemory> rrfFusion(List<ScoredMemory>... resultLists) {
        Map<Long, ScoredMemory> merged = new HashMap<>();
        for (List<ScoredMemory> list : resultLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                ScoredMemory sm = list.get(rank);
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

    /** 时间衰减：days = (now - updatedAt)，factor = exp(-days/30.0)。 */
    private double recencyFactor(MemoryEntry entry) {
        if (entry.getUpdatedAt() == null) {
            return 1.0;
        }
        long days = Duration.between(entry.getUpdatedAt(), java.time.LocalDateTime.now()).toDays();
        return Math.exp(-days / RECENCY_HALF_LIFE_DAYS);
    }

    /** 重要性加权：≥0.7→1.5，≤0.3→0.5，否则 1.0；null 视为 0.5。 */
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

    // 供上游测试/调试：将 ScoredMemory 列表转为 MemoryEntry 列表
    @SuppressWarnings("unused")
    private List<MemoryEntry> toEntries(List<ScoredMemory> list) {
        return list.stream().map(sm -> sm.entry).collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    private static JsonNode silentParse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
