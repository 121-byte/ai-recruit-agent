## ADDED Requirements

### Requirement: vectorSearch 原生 SQL similarity + 排除 archived
HybridMemoryRetriever SHALL 用 JdbcTemplate 直接写 `SELECT …, 1-(embedding <=> ?::vector) AS similarity FROM memory_entry WHERE agent_id=? AND category!='archived' AND embedding IS NOT NULL ORDER BY embedding <=> ?::vector LIMIT 10`；rawScore 用 DB 算的 similarity（非 Java cosine）；keywordSearch 过滤 archived、rawScore=0.5。

#### Scenario: 排除 archived
- **WHEN** 向量检索
- **THEN** SQL WHERE category!='archived'，归档记忆不召回

### Requirement: graphWalk batch UNION
HybridMemoryRetriever SHALL graphWalk 用单条 UNION batch SQL（正反向一次查 related_id+weight），过滤非本人/已归档；rawScore=weight；替代双 in + 逐条 selectById（N+1）。

#### Scenario: 无 N+1
- **WHEN** 图谱游走
- **THEN** 正反向一次 UNION SQL，非逐条 selectById

### Requirement: ScoredMemory source + recency lastAccess + Rerank Top10→Top5
HybridMemoryRetriever SHALL ScoredMemory 加 `source`(vector/keyword/graph)；RRF k=60；recencyFactor 优先 `lastAccess` 回退 `updatedAt`（exp(-days/30)）；importanceFactor ≥0.7→1.5/≤0.3→0.5/else 1.0；记忆 >5 时调 `rerankService.rerank(query, memoryTexts, 5)` 取 Top5。

#### Scenario: Rerank 收敛
- **WHEN** retrieve 返回 >5 条
- **THEN** 调 RerankService Top10→Top5，日志 "Memory rerank: 10 → 5"

#### Scenario: source 标记
- **WHEN** 三路融合
- **THEN** 每条 ScoredMemory 标 source ∈ {vector,keyword,graph}
