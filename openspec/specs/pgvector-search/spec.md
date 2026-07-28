# pgvector-search Specification

## Purpose
TBD - created by archiving change p0-tech-baseline. Update Purpose after archive.
## Requirements
### Requirement: 向量召回走 pgvector 原生 SQL
系统 SHALL 通过 pgvector 的 HNSW 余弦索引（`<=>` 操作符）执行语义向量召回，MUST NOT 在 Java 端对全表记录计算余弦相似度作为召回主路径。召回对象覆盖简历、岗位、记忆条目、文档分块。

#### Scenario: 候选人召回走 pgvector
- **WHEN** CandidateMatchService 对岗位执行 Top20 召回
- **THEN** 调用 DocumentChunkMapper/ResumeMapper 的 searchByVector 原生 SQL（`ORDER BY embedding <=> ?::vector LIMIT 20`）
- **AND** 不出现对 resume 全表逐条 Java cosine 的降级分支

#### Scenario: 记忆向量检索走 pgvector
- **WHEN** HybridMemoryRetriever 对 agentId + query 做向量检索
- **THEN** 经 MemoryEntryMapper.searchByVector 原生 SQL（`ORDER BY embedding <=> ?::vector LIMIT 10`）返回

#### Scenario: VectorSearchService 封装
- **WHEN** Service 层需要语义召回
- **THEN** 调用 service/VectorSearchService.searchCandidates，该方法内部 MUST 调 searchByVector 原生 SQL，cosineSimilarity 仅作工具方法不用于召回主路径

### Requirement: 删除 H2 数据源降级
系统 MUST NOT 在应用启动时因 PostgreSQL 不可用而回退到嵌入式 H2 数据源。`app.mock.enabled` 不再控制数据源类型，数据源固定为 application.properties 中 `spring.datasource.*` 指定的 PostgreSQL+pgvector。

#### Scenario: 无 H2 依赖与 Bean
- **WHEN** 项目编译/启动
- **THEN** pom 中无 h2 依赖
- **AND** DataSourceConfig 无 @ConditionalOnProperty(app.mock.enabled) 的 H2 Bean

#### Scenario: PG 不可用时启动失败
- **WHEN** PostgreSQL 不可达且未配置降级
- **THEN** 应用启动失败（连接超时）而非静默回退 H2

