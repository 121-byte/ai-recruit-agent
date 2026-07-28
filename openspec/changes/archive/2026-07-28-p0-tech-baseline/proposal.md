## Why

复刻项目为兼容无 PostgreSQL 环境引入了 H2 降级与 Java 端余弦相似度召回，偏离原项目（AImianshi）以 pgvector 为权威的向量检索实现。现已接入真实远程 PG+pgvector，需回归原生 pgvector 检索、删除降级逻辑，并补回原项目由 Mapper XML 实现的自定义查询（向量/join/批量/聚合），为 P1 Service 层移植提供数据访问基础。依据《复刻项目迁移对齐清单》§1/§2/§4。

## What Changes

- **BREAKING** 删除 Java 端余弦相似度召回降级：`CandidateMatchingTool` 改回经 `VectorSearchService` 调 pgvector `<=>` 原生 SQL，不再在 Java 端对全表算 cosine。
- 删除 H2 降级数据源：`DataSourceConfig` 移除 `@ConditionalOnProperty(app.mock.enabled)` 的 H2 Bean 与 H2 相关分支，回归 PostgreSQL 自动配置。
- POM 依赖对齐：删除 `h2`；补 `postgis-jdbc`、`jackson-databind`（显式）；`poi`/`poi-scratchpad` 收敛为仅 `poi-ooxml`；保留 `mybatis-plus-spring-boot3-starter`。
- 新建 `service/VectorSearchService`：封装 `searchCandidates`（调 `DocumentChunkMapper.searchByVector` 原生 SQL）；`cosineSimilarity` 仅作工具，召回走 pgvector。
- 等价补回 Mapper 自定义查询（@Select/@Insert 注解或 XML，向量/join/批量/聚合必原生 SQL）：
  - `DocumentChunkMapper`: searchByVector / searchByVectorWithFilter / batchInsert / selectByParent / countByParentType / deleteByParent
  - `ResumeMapper`: updateEmbedding / selectByFilter / selectByIds / selectByStatus / count / countByStatus
  - `JobProfileMapper`: updateEmbedding / selectByFilter
  - `MemoryEntryMapper`: searchByKeyword / findByAgentId / findByAgentIdAndCategory / incrementAccessCount / updateImportance / applyDecay / archiveLowImportance / deleteByAgentIdAndKey（已有的保留）
  - `AgentTraceMapper`: batchInsert / selectBySessionId / selectByAgentName / countByAgentName / countDistinctSessions / countSessionsWithToolCalls / countCompletedSessions
  - `CandidateMatchMapper`: selectByJobIdWithResume(join) / selectByJobAndResume / deleteByResumeId / deleteByJobId / count
  - `ChatMessageMapper`: sumTokensBySessionId / sumTokensByAgentId / countBySessionId / deleteBySessionId
  - `ChatSessionMapper`: softDelete / updateTitle / selectByAgentId
  - `InterviewMapper`: selectByJobId / selectByResumeId / selectByStatus / countByStatus / deleteByResumeId / deleteByJobId
  - `InterviewSessionMapper`: appendMessage / selectActiveSessions
  - `InterviewReportMapper`: selectByInterviewIds(in) / count
  - `OutreachMapper`: batchInsert / batchUpdateStatus / countByStatus / selectByBatchId
  - `EvaluationGoldenSampleMapper`: selectActive / selectByCategory
  - `EvaluationResultMapper`: avgScoreByCategory(聚合) / selectBySampleId
  - `InterviewEvaluationMapper`: selectByInterviewId / deleteByInterviewId
  - `QuestionMapper`: batchInsert / adoptQuestion / deleteByInterviewId
  - `HrPreferenceMapper`: upsert / deleteExpired
  - `SysRoleMapper`: selectRoleCodesByUserId(join) / selectAll
  - `SysUserMapper`: findByUsername / findWithRolesById(join) / selectAll / update / updateStatus / updateLastLoginAt
  - `SysUserRoleMapper`: insertBatch / selectByUserId
- 补 `PgArrayTypeHandler` 处理 `BIGINT[]`（如 `consolidation_task.entry_ids`）；保留 `FloatVectorTypeHandler` 处理 `vector`；核对 entity `@TableField(typeHandler=...)` 标注一致。

## Capabilities

### New Capabilities
- `pgvector-search`: 向量检索能力——简历/岗位/记忆/分块的语义召回统一走 pgvector HNSW 余弦原生 SQL，无 Java 端降级。
- `data-access-queries`: 数据访问自定义查询能力——Service 层所需的向量检索、join、批量、聚合类查询在 Mapper 层以原生 SQL 等价提供。

### Modified Capabilities
（无现有 specs，本变更为首次引入）

## Impact

- **代码**：`CandidateMatchingTool`（删除 Java cosine 降级、改调 VectorSearchService）、`DataSourceConfig`（删 H2 分支）、新增 `service/VectorSearchService`、新增 `dal/handler/PgArrayTypeHandler`、20+ Mapper 增补自定义方法。
- **依赖**：pom.xml（删 h2、补 postgis-jdbc/jackson-databind、poi 收敛）。
- **配置**：application.properties 的 `app.mock.enabled` 不再触发 H2 数据源（保留 AI 服务 Mock 降级逻辑不变，仅数据源回归 PG）。
- **API**：无对外契约变化（本阶段为内部数据访问层对齐）。
- **风险**：删除 H2 降级后，无 PostgreSQL 环境将无法启动（已具备远程 PG，可接受）。
