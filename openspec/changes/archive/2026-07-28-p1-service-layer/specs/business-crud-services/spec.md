## ADDED Requirements

### Requirement: 岗位领域服务
JobAnalysisService SHALL 提供 analyze(jobId)（LLM 解析 JD → weight_matrix/role_graph/growth_path → 写 job_profile + embedding）。JobProfileService SHALL 提供 create/update/delete/getById/listAll/listByFilter/listByStatus/updateEmbedding。

#### Scenario: 岗位分析
- **WHEN** 调用 JobAnalysisService.analyze(jobId)
- **THEN** DeepSeek 解析 JD → 写 weight_matrix/role_graph/growth_path + 算 embedding

### Requirement: 简历领域服务
ResumeService SHALL 提供 create/update/delete/getById/listAll/listByFilter/listByStatus/updateEmbedding。

#### Scenario: 更新简历向量
- **WHEN** 调用 updateEmbedding(id, vector)
- **THEN** 经 ResumeMapper.updateEmbedding 原生 SQL 写 embedding

### Requirement: 面试领域服务
InterviewService SHALL 提供 create/update/updateStatus/getById/listByJobId/listByResumeId/listAll/deleteByJobId/deleteByResumeId + 题目管理 addQuestion/batchAddQuestions/adoptQuestion/listQuestions/listAdoptedQuestions/getQuestionById。QuestionService SHALL 提供 generateQuestions(interviewId)。

#### Scenario: 生成面试题
- **WHEN** 调用 QuestionService.generateQuestions(interviewId)
- **THEN** DeepSeek 生成技术/项目/行为三类题 + follow_ups → batchInsert question

### Requirement: 用户与偏好与导出与追踪
UserService SHALL 提供 create/update/delete/listAll/assignRoles。HrPreferenceService SHALL 提供 getByHrId/save/cleanExpired。ExportService SHALL 提供 exportSession(sessionId)。AgentTraceService SHALL 提供 record/batchRecord/getSessionTrace/listByAgent/countByAgent/getAllSessionCount/getCompletedSessions/getSessionsWithToolCalls（从 agent/event/ 归位到 service/）。MemoryService SHALL 作为记忆服务门面统一访问长期/短期记忆。

#### Scenario: 用户角色分配
- **WHEN** 调用 assignRoles(userId, roleCodes)
- **THEN** 重置 sys_user_role 关联

#### Scenario: 会话导出
- **WHEN** 调用 exportSession(sessionId)
- **THEN** 聚合 AgentTrace + ChatMessage → 返回导出文本
