## ADDED Requirements

### Requirement: 分块与向量化
DocumentChunkService SHALL 提供 chunkAndEmbedJob(jobId) / chunkAndEmbedResume(resumeId)：从 parsed_json 提取 skill/experience/education/summary 分块，逐块 EmbeddingService.embed，批量插入 document_chunk。

#### Scenario: 简历分块入库
- **WHEN** 调用 chunkAndEmbedResume(resumeId)
- **THEN** 按 skill/experience/education/summary 生成 chunk → embed → batchInsert document_chunk
- **AND** UNIQUE(parent_type,parent_id,chunk_index) 去重

### Requirement: 依赖向量检索
DocumentChunkService 的语义召回 SHALL 经 DocumentChunkMapper.searchByVector 原生 SQL，MUST NOT 在 Java 端算 cosine 召回。

#### Scenario: 分块召回
- **WHEN** 调用 searchByVector 路径
- **THEN** 走 pgvector `<=>` 原生 SQL
