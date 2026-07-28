# candidate-matching Specification

## Purpose
TBD - created by archiving change p1-service-layer. Update Purpose after archive.
## Requirements
### Requirement: 四阶段匹配全流程
CandidateMatchService SHALL 对岗位执行四阶段匹配：(1) pgvector Top20 召回 + 方向预过滤；(2) 候选池 ≤10 时条件性 rerank；(3) LLM 三维评分 skill/experience/soft；(4) 透明加权 finalScore = skill*0.4 + exp*0.3 + soft*0.2 + vector*0.1，并自动创建 interview 记录。

#### Scenario: 执行匹配
- **WHEN** 调用 matchForJob(jobId)
- **THEN** 经 VectorSearchService 召回 → 条件性 RerankService.rerank → DeepSeekModelService.chatJson 三维评分 → 加权排序 → 写 candidate_match + 创建 interview
- **AND** 返回 Top5 含各维度分数与 interview_id

#### Scenario: 方向预过滤为空时回退
- **WHEN** extractPositionFilters 返回空或过滤后无结果
- **THEN** 回退到无过滤向量召回，不抛异常

### Requirement: CRUD 与查询
CandidateMatchService SHALL 提供 create/update/getById/getByJobAndResume/listByJobId/listSortedByJob/feedback。

#### Scenario: 按岗位列出
- **WHEN** 调用 listByJobId(jobId)
- **THEN** 经 CandidateMatchMapper.selectByJobIdWithResume(join) 返回带候选人姓名的列表

#### Scenario: HR 反馈
- **WHEN** 调用 feedback(id, text)
- **THEN** 更新 candidate_match.hr_feedback

