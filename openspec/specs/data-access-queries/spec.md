# data-access-queries Specification

## Purpose
TBD - created by archiving change p0-tech-baseline. Update Purpose after archive.
## Requirements
### Requirement: 向量检索类自定义查询以原生 SQL 提供
系统 SHALL 在 Mapper 层以原生 SQL（@Select/@Insert 注解或 XML）提供向量检索自定义方法，MUST NOT 用 LambdaQueryWrapper 等价向量检索。必含：DocumentChunkMapper.searchByVector、searchByVectorWithFilter、ResumeMapper/JobProfileMapper/MemoryEntryMapper 的向量检索与 updateEmbedding。

#### Scenario: 分块向量检索
- **WHEN** 调用 DocumentChunkMapper.searchByVector(queryVector, parentType, topK)
- **THEN** 执行 `SELECT * FROM document_chunk WHERE parent_type=? ORDER BY embedding <=> ?::vector LIMIT ?` 原生 SQL

#### Scenario: 向量更新
- **WHEN** 调用 ResumeMapper.updateEmbedding(id, embedding)
- **THEN** 执行 `UPDATE resume SET embedding=?::vector WHERE id=?` 原生 SQL

### Requirement: join 类自定义查询以原生 SQL 提供
系统 SHALL 以原生 SQL 提供 join 查询：CandidateMatchMapper.selectByJobIdWithResume（candidate_match JOIN resume）、SysRoleMapper.selectRoleCodesByUserId（sys_user_role JOIN sys_role）、SysUserMapper.findWithRolesById。

#### Scenario: 岗位匹配带简历
- **WHEN** 调用 CandidateMatchMapper.selectByJobIdWithResume(jobId)
- **THEN** 执行 `SELECT cm.*, r.candidate_name FROM candidate_match cm LEFT JOIN resume r ON cm.resume_id=r.id WHERE cm.job_id=?`

#### Scenario: 用户角色码
- **WHEN** 调用 SysRoleMapper.selectRoleCodesByUserId(userId)
- **THEN** 执行 join sys_user_role + sys_role 返回角色 code 列表

### Requirement: 批量与聚合类自定义查询以原生 SQL 提供
系统 SHALL 以原生 SQL 提供批量与聚合：AgentTraceMapper.batchInsert、ChatMessageMapper.sumTokensBySessionId、InterviewReportMapper.selectByInterviewIds(in)、EvaluationResultMapper.avgScoreByCategory、OutreachMapper.batchInsert/batchUpdateStatus、QuestionMapper.batchInsert/adoptQuestion、SysUserRoleMapper.insertBatch、HrPreferenceMapper.upsert。

#### Scenario: 批量插入追踪
- **WHEN** 调用 AgentTraceMapper.batchInsert(list)
- **THEN** 单次批量 INSERT 原生 SQL 写入多条记录

#### Scenario: 按分类平均分
- **WHEN** 调用 EvaluationResultMapper.avgScoreByCategory()
- **THEN** 执行 `SELECT category, AVG(score) FROM ... GROUP BY category` 聚合 SQL

#### Scenario: 批量查报告
- **WHEN** 调用 InterviewReportMapper.selectByInterviewIds(ids)
- **THEN** 执行 `WHERE interview_id IN (...)` 原生 SQL

### Requirement: 数组类型处理器
系统 SHALL 提供 PgArrayTypeHandler 处理 PostgreSQL `BIGINT[]`/`TEXT[]` 数组列（如 consolidation_task.entry_ids），并保留 FloatVectorTypeHandler 处理 `vector` 类型。含数组列的 entity MUST 以 @TableField(typeHandler=...) 正确标注。

#### Scenario: BIGINT 数组读写
- **WHEN** 读写 consolidation_task.entry_ids（BIGINT[]）
- **THEN** 经 PgArrayTypeHandler 在 String[]/Long[] 与 SQL ARRAY 间转换

#### Scenario: vector 列读写
- **WHEN** 读写 resume.embedding（VECTOR(1024)）
- **THEN** 经 FloatVectorTypeHandler 在 float[] 与 pgvector 字面量间转换

