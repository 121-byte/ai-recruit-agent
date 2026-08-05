package com.example.recruit.memory;

import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.example.recruit.infra.retrieval.EmbeddingService;
import com.example.recruit.infra.retrieval.RerankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 *   <li>RRF k=60; 记忆&gt;5 时调记忆专用 rerank 取 Top5</li>
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
    private static final List<String> KEYWORD_STOP_TERMS = List.of(
            "有哪些", "怎么样", "以及", "哪些", "什么", "的", "和", "与", "及",
            "情况", "候选人", "岗位", "要求", "方向", "经验", "项目", "背景",
            "相关", "负责", "参与", "会用");

    /** 命中记录：cacheKey = agentId + ":" + query。 */
    private static final ThreadLocal<Map<String, List<ScoredMemory>>> CACHE =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private final MemoryEntryMapper memoryEntryMapper;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final RerankService rerankService;
    private final AppProperties appProperties;

    public HybridMemoryRetriever(MemoryEntryMapper memoryEntryMapper,
                                 EmbeddingService embeddingService,
                                 JdbcTemplate jdbcTemplate,
                                 RerankService rerankService,
                                 AppProperties appProperties) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.rerankService = rerankService;
        this.appProperties = appProperties;
    }

    /**
     * 检索结果记录。字段公开以便融合算法直接读写。
     */
    public static class ScoredMemory {
        public MemoryEntry entry;
        public double rawScore;
        public double rrfScore;
        public double finalScore;
        public double directMatchScore;
        public String source;  // vector/keyword/graph

        public ScoredMemory(MemoryEntry entry, double rawScore, String source) {
            this.entry = entry;
            this.rawScore = rawScore;
            this.rrfScore = 0.0;
            this.finalScore = 0.0;
            this.directMatchScore = 0.0;
            this.source = source;
        }
    }

    /**
     * 混合检索主入口。
     */
    public List<ScoredMemory> retrieve(String agentId, String query) {
        return retrieve(agentId, query, true);
    }

    /**
     * 只读检索入口，供离线评估使用，避免评估运行改变 access_count / last_access / ttl。
     */
    public List<ScoredMemory> retrieveReadOnly(String agentId, String query) {
        return retrieve(agentId, query, false);
    }

    private List<ScoredMemory> retrieve(String agentId, String query, boolean renewAccess) {
        String cacheKey = (renewAccess ? "rw:" : "ro:") + agentId + ":" + query;
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
                List<Integer> rerankIndices = rerankService.rerankMemory(query, texts, RERANK_TOP);
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
        result = applyFinalDirectMatchThreshold(query, result);

        if (renewAccess) {
            // 检索命中续期: 批量 access_count+1 / last_access / ttl_expires_at (艾宾浩斯留存曲线, 一次 UPDATE)
            renewAccessForHits(result);
        }

        CACHE.get().put(cacheKey, result);
        return result;
    }

    /** 对命中记忆批量续期 (同请求只续一次); 失败仅 log, 不影响检索主流程。 */
    private void renewAccessForHits(List<ScoredMemory> result) {
        if (result.isEmpty()) {
            return;
        }
        List<Long> ids = result.stream()
                .map(sm -> sm.entry.getId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        try {
            AppProperties.Memory mem = appProperties.getMemory();
            double baseTtlSeconds = mem.getHalfLifeDays() * 86400.0 * Math.log(1.0 / mem.getForgetThreshold());
            long minRetentionSeconds = (long) mem.getMinRetentionDays() * 86400L;
            memoryEntryMapper.renewAccess(ids, baseTtlSeconds,
                    mem.getKImportance(), mem.getKAccess(), minRetentionSeconds);
        } catch (Exception e) {
            log.debug("renewAccess failed: {}", e.getMessage());
        }
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
        double minSimilarity = appProperties.getMemory().getVectorMinSimilarity();
        String sql = "SELECT id, agent_id, memory_key, memory_value, category, tags, access_count, " +
                "last_access, importance, embedding, created_at, updated_at, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM memory_entry WHERE agent_id = ? AND category != 'archived' " +
                "AND embedding IS NOT NULL " +
                "AND (ttl_expires_at IS NULL OR ttl_expires_at > NOW()) " +
                "AND (? <= 0 OR 1 - (embedding <=> ?::vector) >= ?) " +
                "ORDER BY embedding <=> ?::vector LIMIT 10";
        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> mapScoredMemory(rs, "vector"),
                    literal, agentId, minSimilarity, literal, minSimilarity, literal);
        } catch (Exception e) {
            log.debug("vectorSearch failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 关键词检索: 对 query 做轻量拆词, 覆盖 key/value/tags, rawScore=0.5。 */
    private List<ScoredMemory> keywordSearch(String agentId, String query) {
        try {
            Map<Long, ScoredMemory> result = new LinkedHashMap<>();
            for (String keyword : keywordQueries(query)) {
                List<MemoryEntry> entries = memoryEntryMapper.searchByKeyword(agentId, keyword);
                for (MemoryEntry e : entries) {
                    if (e.getId() == null || result.containsKey(e.getId())) {
                        continue;
                    }
                    result.put(e.getId(), new ScoredMemory(e, 0.5, "keyword"));
                }
            }
            return new ArrayList<>(result.values());
        } catch (Exception e) {
            log.debug("keywordSearch failed (agent={}): {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> keywordQueries(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, query.trim());

        String segmented = query.trim();
        for (String term : KEYWORD_STOP_TERMS) {
            segmented = segmented.replace(term, " ");
        }
        segmented = segmented.replaceAll("[^\\p{IsHan}A-Za-z0-9+#.]+", " ");
        for (String token : segmented.split("\\s+")) {
            addKeyword(keywords, token);
        }
        return new ArrayList<>(keywords);
    }

    private void addKeyword(Set<String> keywords, String keyword) {
        if (keyword == null) {
            return;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() >= 2 && !KEYWORD_STOP_TERMS.contains(trimmed)) {
            keywords.add(trimmed);
        }
    }

    private List<ScoredMemory> applyFinalDirectMatchThreshold(String query, List<ScoredMemory> result) {
        double minScore = appProperties.getMemory().getFinalMinDirectMatchScore();
        if (minScore <= 0 || result.isEmpty()) {
            return result;
        }
        List<String> terms = directMatchTerms(query);
        QuerySlot slot = detectSlot(query);
        List<ScoredMemory> filtered = new ArrayList<>();
        for (ScoredMemory sm : result) {
            sm.directMatchScore = directMatchScore(sm, terms, slot);
            if (sm.directMatchScore >= minScore) {
                filtered.add(sm);
            }
        }
        return filtered;
    }

    private List<String> directMatchTerms(String query) {
        List<String> terms = keywordQueries(query).stream()
                .filter(term -> !KEYWORD_STOP_TERMS.contains(term))
                .filter(term -> term.length() >= 2)
                .toList();
        if (terms.size() <= 1) {
            return terms;
        }
        String original = query == null ? "" : query.trim();
        return terms.stream()
                .filter(term -> !term.equals(original))
                .toList();
    }

    private double directMatchScore(ScoredMemory sm, List<String> terms, QuerySlot slot) {
        String text = searchableText(sm.entry);
        double termScore = 0.0;
        if (!terms.isEmpty()) {
            long matches = terms.stream().filter(text::contains).count();
            termScore = (double) matches / terms.size();
        }
        double slotScore = slot.matches(text) ? 1.0 : 0.0;
        double sourceScore = sm.source != null && sm.source.contains("keyword") ? 0.1 : 0.0;
        return Math.min(1.0, termScore * 0.6 + slotScore * 0.4 + sourceScore);
    }

    private String searchableText(MemoryEntry entry) {
        if (entry == null) {
            return "";
        }
        String tags = entry.getTags() == null ? "" : String.join(",", entry.getTags());
        return String.join(" ",
                nullToEmpty(entry.getMemoryKey()),
                nullToEmpty(entry.getMemoryValue()),
                tags);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private QuerySlot detectSlot(String query) {
        String q = query == null ? "" : query;
        if (containsAny(q, "技术栈", "技术能力", "技能", "会用")) {
            return new QuerySlot("techstack", List.of("techstack", "技术栈", "技术能力", "技能", "熟悉", "精通"));
        }
        if (containsAny(q, "年以上", "年经验", "工作年限", "年限")) {
            return new QuerySlot("years", List.of("candidate:", "年经验", "年,", "年，", "年以上"));
        }
        if (containsAny(q, "项目", "系统", "负责", "参与")) {
            return new QuerySlot("project", List.of("project", "项目", "系统", "负责", "参与", "主导"));
        }
        if (containsAny(q, "面试", "面评")) {
            return new QuerySlot("interview", List.of("interview", "面试", "面评"));
        }
        if (containsAny(q, "offer", "薪资", "入职", "录用")) {
            return new QuerySlot("offer", List.of("offer", "薪资", "入职", "录用"));
        }
        if (containsAny(q, "教育", "学历", "学校", "专业", "本科", "专科")) {
            return new QuerySlot("education", List.of("education", "教育", "学历", "学校", "专业", "本科", "专科"));
        }
        if (containsAny(q, "岗位要求", "岗位需求", "要求", "需求")) {
            return new QuerySlot("requirement", List.of("req", "岗位要求", "岗位需求", "要求", "需求"));
        }
        if (containsAny(q, "人才分层", "分层")) {
            return new QuerySlot("talent-tier", List.of("talent-tier", "人才分层", "分层"));
        }
        if (containsAny(q, "候选人", "人才库", "推荐")) {
            return new QuerySlot("candidate", List.of("candidate:", "人才库", "候选人", "推荐"));
        }
        return QuerySlot.NONE;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private record QuerySlot(String name, List<String> aliases) {
        static final QuerySlot NONE = new QuerySlot("none", List.of());

        boolean matches(String text) {
            if (this == NONE) {
                return false;
            }
            return aliases.stream().anyMatch(text::contains);
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

        // 批量加载关联记忆, 过滤非本人/已归档/已过期
        List<MemoryEntry> related = memoryEntryMapper.selectBatchIds(relatedWeights.keySet());
        List<ScoredMemory> result = new ArrayList<>();
        for (MemoryEntry e : related) {
            if (e == null || e.getId() == null) continue;
            // 过滤非本人
            if (!agentId.equals(e.getAgentId())) continue;
            // 过滤已归档
            if ("archived".equals(e.getCategory())) continue;
            // 过滤 TTL 已过期 (对齐 Hebb: 检索时不可见, 也不被续期复活)
            if (isExpired(e)) continue;
            // 过滤已在 seed 中的
            if (seeds.contains(e.getId())) continue;
            double w = relatedWeights.getOrDefault(e.getId(), 0.5);
            result.add(new ScoredMemory(e, w, "graph"));
        }
        return result;
    }

    /** TTL 已过期判定: ttl_expires_at 早于当前时刻 (null 视为未预算, 未过期)。 */
    private boolean isExpired(MemoryEntry e) {
        return e.getTtlExpiresAt() != null
                && e.getTtlExpiresAt().isBefore(LocalDateTime.now());
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
                    // 累加命中路径: 同一记忆被多路召回时, source 标记所有贡献路径 (vector+keyword+graph),
                    // 避免先入为主把 keyword/graph 的贡献遮蔽 (评估三路占比此前 keyword 恒 0%)。
                    if (sm.source != null && !sm.source.isEmpty()
                            && !existing.source.contains(sm.source)) {
                        existing.source = existing.source + "+" + sm.source;
                    }
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
        entry.setTags(toStringArray(rs.getArray("tags")));
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

    /** Postgres TEXT[] → String[]; null/异常返回空数组。 */
    private String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return new String[0];
        }
        Object arr = array.getArray();
        if (arr instanceof Object[] oa) {
            String[] result = new String[oa.length];
            for (int i = 0; i < oa.length; i++) {
                result[i] = oa[i] == null ? null : oa[i].toString();
            }
            return result;
        }
        return new String[0];
    }
}
