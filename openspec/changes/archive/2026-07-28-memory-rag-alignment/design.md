## Context

当前记忆+RAG 与参考项目多处行为偏离（详见《记忆与RAG-参考项目复刻对齐改进文档》）。本变更按路径 B（行为/契约复刻）对齐，保留 float[]+TypeHandler+MyBatis-Plus+mock 类型化优势。M1-M4 四阶段 16 步。

## Goals / Non-Goals

**Goals**
- 记忆读写闭环（短期 List/长期 store-upsert-get-delete/门面迁包/提取注入检测/巩固 claimTask+ON CONFLICT/衰减遗忘 DISTINCT）。
- 混合检索大脑（JdbcTemplate+Rerank Top10→Top5+source+UNION graphWalk+排除 archived）。
- RAG 分块级召回主路径（GROUP BY parent_id+5 类语义段+selectByIds）。
- 表结构对齐（UNIQUE/completed_at/id 主键）。
- ContextAssembler 注入防御提示词。

**Non-Goals**
- 不回退手写 XML+String embedding（路径 A，代价大放弃类型安全）。
- 不改对外 API 契约（记忆/RAG 是内部）。
- 不在 ContextAssembler 注入简历/岗位 RAG（两项目共同缺口，未来增强）。

## Decisions

### D1: 路径 B（行为/契约复刻）
- 保留 float[]+FloatVectorTypeHandler+MyBatis-Plus+@Data+mock；仅对齐行为/契约/表。
- **理由**: 类型安全 + 可演示，文档默认推荐。

### D2: 引入 JdbcTemplate
- HybridMemoryRetriever/ConsolidationScheduler/MemoryConsolidationAgent/MemoryDecayJob/MemoryForgettingService 注入 JdbcTemplate 做原生 SQL（DISTINCT/UNION/ON CONFLICT/claimTask GROUP BY）。
- **理由**: 复杂 SQL（GROUP BY parent_id/UNION/ON CONFLICT）注解 SQL 表达力不足，JdbcTemplate 直接写更对齐参考。

### D3: Embedding/Rerank 模型适配（非参考但可用）
- Embedding 保留 `qwen3.7-text-embedding`（v3 额度耗尽），对齐行为（embed key+value、失败抛异常语义、保留 mock 标注）。
- Rerank 保留原生端点+`qwen3-vl-rerank`（/reranks 实测 404），仅补 instruct。
- **理由**: 用户已验证可用的配置；切回参考模型会断（v3 额度耗尽、/reranks 404）。行为对齐即可。

### D4: MemoryService 迁回 memory/ 包
- service.MemoryService → memory.MemoryService，更新引用方 import。
- **理由**: 文档要求与参考一致（参考门面在 memory/）。

### D5: tags 列保留但不落库
- memory_entry.tags 列保留（不破坏兼容），巩固时仅 log.debug 不写库（对齐参考行为）。
- **理由**: 参考无 tags 列但删列破坏性大；保留+停写达成行为一致。

## Risks / Trade-offs

- [Risk] MemoryService 迁包 + save→store/upsert 改名破坏调用方 → Mitigation: 全量更新 import + 调用方方法名。
- [Risk] claimTask + ON CONFLICT 需表 UNIQUE（M4）→ Mitigation: M4 先改表，M1/M2 应用层后依赖。
- [Risk] 分块召回需 document_chunk 数据 → Mitigation: ChunkBackfillRunner 已回填 + DocumentChunkService 5 类切分。
- [Risk] 引入 JdbcTemplate 与 MyBatis-Plus 双数据访问 → Mitigation: 同一 DataSource，无冲突。

## Migration Plan

M1 记忆深度对齐（应用层，不改表）：EmbeddingService 行为 → AutoMemoryExtractor 注入检测 → PostgresLongTermMemory store/upsert/get/delete + embed(key+value) → MemoryService 迁包+改名 → MemoryConsolidationAgent chatFast+tags 不落库 → ConsolidationScheduler claimTask+DISTINCT+50。
M2 混合检索：RerankService instruct → HybridMemoryRetriever JdbcTemplate+Rerank+source+UNION+archived+Top10→Top5。
M3 RAG 主路径：DocumentChunkMapper GROUP BY+JOIN → ResumeMapper selectByIds → VectorSearchService 分块召回 → DocumentChunkService 5 类 → ContextAssembler §+注入防御。
M4 表+调度：schema 4 表改 → MemoryDecayJob/MemoryForgettingService DISTINCT+applyDecay 条件 → RedisSessionMemory StringRedisTemplate+List。

## Open Questions

- consolidation_task 的 result 列当前是 JSONB+JacksonTypeHandler，改 JdbcTemplate 写需 `?::jsonb` 字符串——保留兼容。
