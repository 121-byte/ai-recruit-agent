# Tasks: p0-tech-baseline

## 1. POM 依赖对齐

- [x] 1.1 删除 pom.xml 中 `com.h2database:h2` 依赖
- [x] 1.2 新增 `net.postgis:postgis-jdbc` 依赖（与原项目版本对齐）
- [x] 1.3 新增 `com.fasterxml.jackson.core:jackson-databind` 显式依赖
- [x] 1.4 删除 `org.apache.poi:poi` 与 `poi-scratchpad`，仅保留 `poi-ooxml`
- [x] 1.5 `mvn compile` 验证依赖解析无错

## 2. 类型处理器与 entity 标注

- [x] 2.1 新增 `dal/handler/PgArrayTypeHandler`（String[]/Long[] ↔ PG BIGINT[]/TEXT[] ARRAY，用 connection.createArrayOf）
- [x] 2.2 核对含 embedding 列的 entity（Resume/JobProfile/MemoryEntry/DocumentChunk）`@TableField(typeHandler=FloatVectorTypeHandler.class)` 标注齐全
- [x] 2.3 核对含数组列的 entity（ConsolidationTask.entry_ids、MemoryEntry.tags、Resume.riskTags、InterviewReport.strengths/risks、InterviewEvaluation.tags）`@TableField(typeHandler=PgArrayTypeHandler.class)` 或全局 @MappedTypes 生效

## 3. Mapper 自定义查询补回（向量类）

- [x] 3.1 DocumentChunkMapper: searchByVector / searchByVectorWithFilter / batchInsert / selectByParent / countByParentType / deleteByParent
- [x] 3.2 ResumeMapper: updateEmbedding / selectByFilter / selectByIds / selectByStatus / count / countByStatus
- [x] 3.3 JobProfileMapper: updateEmbedding / selectByFilter
- [x] 3.4 MemoryEntryMapper: 核对已有 searchByKeyword/applyDecay/archiveLowImportance/deleteLowest，补 findByAgentId / findByAgentIdAndCategory / incrementAccessCount / updateImportance / deleteByAgentIdAndKey

## 4. Mapper 自定义查询补回（join/批量/聚合类）

- [x] 4.1 CandidateMatchMapper: selectByJobIdWithResume(join resume) / selectByJobAndResume / deleteByResumeId / deleteByJobId / count
- [x] 4.2 AgentTraceMapper: batchInsert / selectBySessionId / selectByAgentName / countByAgentName / countDistinctSessions / countSessionsWithToolCalls / countCompletedSessions
- [x] 4.3 ChatMessageMapper: sumTokensBySessionId / sumTokensByAgentId / countBySessionId / deleteBySessionId
- [x] 4.4 ChatSessionMapper: softDelete / updateTitle / selectByAgentId
- [x] 4.5 InterviewMapper: selectByJobId / selectByResumeId / selectByStatus / countByStatus / deleteByResumeId / deleteByJobId
- [x] 4.6 InterviewSessionMapper: appendMessage / selectActiveSessions
- [x] 4.7 InterviewReportMapper: selectByInterviewIds(in) / count
- [x] 4.8 OutreachMapper: batchInsert / batchUpdateStatus / countByStatus / selectByBatchId
- [x] 4.9 EvaluationGoldenSampleMapper: selectActive / selectByCategory
- [x] 4.10 EvaluationResultMapper: avgScoreByCategory(聚合) / selectBySampleId
- [x] 4.11 InterviewEvaluationMapper: selectByInterviewId / deleteByInterviewId
- [x] 4.12 QuestionMapper: batchInsert / adoptQuestion / deleteByInterviewId
- [x] 4.13 HrPreferenceMapper: upsert / deleteExpired
- [x] 4.14 SysRoleMapper: selectRoleCodesByUserId(join) / selectAll
- [x] 4.15 SysUserMapper: findByUsername / findWithRolesById(join) / selectAll / update / updateStatus / updateLastLoginAt
- [x] 4.16 SysUserRoleMapper: insertBatch / selectByUserId

## 5. VectorSearchService 与降级删除

- [x] 5.1 新建 `service/VectorSearchService`：searchCandidates 调 DocumentChunkMapper.searchByVector；cosineSimilarity 工具方法（FloatVectorTypeHandler.cosine 复用）
- [x] 5.2 CandidateMatchingTool: 删除 Java 端余弦召回降级（recallResumes 改调 VectorSearchService.searchCandidates），删除 H2 兼容注释
- [x] 5.3 DataSourceConfig: 删除 mockDataSource() H2 Bean 与 @ConditionalOnProperty 分支
- [x] 5.4 EmbeddingService/RerankService/DeepSeekModelService: 清理"H2/Mock 降级"措辞注释（保留无 key 时的 AI Mock 降级逻辑）

## 6. 验证

- [x] 6.1 `mvn clean compile -DskipTests` 通过
- [x] 6.2 启动连远程 PG，spring.sql.init 建表 + CREATE EXTENSION vector/pg_trgm 成功
- [x] 6.3 curl 创建岗位 → matchCandidates 触发 searchByVector 原生 SQL（查日志确认无 Java cosine 降级）
- [x] 6.4 `openspec validate p0-tech-baseline` 通过
