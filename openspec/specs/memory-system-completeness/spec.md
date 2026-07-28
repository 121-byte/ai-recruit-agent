# memory-system-completeness Specification

## Purpose
TBD - created by archiving change p4-diff-backfill. Update Purpose after archive.
## Requirements
### Requirement: 三层记忆 + 图谱 + 巩固 + 双重遗忘 + 混合检索
memory/* SHALL 完整实现：RedisSessionMemory（短期+渐进式压缩）、PostgresLongTermMemory（save/searchByVector/searchByKeyword/getByCategory/getAll/updateImportance）、memory_graph（图谱边）、HybridMemoryRetriever（向量+关键词+图谱游走+RRF 融合 k=60+时间衰减 30 天半衰期+重要性加权）、AutoMemoryExtractor（关键词预过滤+LLM 提取）、MemoryConsolidationAgent（7 步巩固）、ConsolidationScheduler（双触发≥10）、MemoryDecayJob（日 0.95/30 天/0.05 归档）、MemoryForgettingService（时 0.8/14 天/0.15 归档/200 容量驱逐）。

#### Scenario: RRF 融合
- **WHEN** HybridMemoryRetriever.retrieve(agentId, query)
- **THEN** 三路（向量/关键词/图谱）RRF 融合（k=60）+ 时间衰减 + 重要性加权后 Top10

#### Scenario: 双重遗忘
- **WHEN** MemoryDecayJob（日 3:30）与 MemoryForgettingService（时 :30）触发
- **THEN** 衰减（≥0.7 豁免）+ 归档 + 容量驱逐（200/用户）各自执行

### Requirement: AgentTraceService 归位引用统一
AgentTraceService 的写入（record/batchRecord）与读取（getSessionTrace/listByAgent/countByAgent/统计）SHALL 统一引用，消除 P1 的 AgentTraceReadService/AgentTraceService 双份并存的临时状态。

#### Scenario: 统一引用
- **WHEN** ConversationAgentService doOnComplete 记录 trace
- **THEN** 调归位后的单一 AgentTraceService.record

