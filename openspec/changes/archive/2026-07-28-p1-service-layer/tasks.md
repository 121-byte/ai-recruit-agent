# Tasks: p1-service-layer

## 1. analysis POJO

- [x] 1.1 新建 service/analysis/StructuredData（fromJson/toJson/toJsonNode）
- [x] 1.2 新建 service/analysis/ImplicitInsights
- [x] 1.3 新建 service/analysis/PotentialAssessment
- [x] 1.4 新建 service/analysis/RiskAssessment
- [x] 1.5 新建 service/analysis/ComparisonResult
- [x] 1.6 新建 service/analysis/ResumeAnalysisResult（聚合上述）

## 2. CRUD Service（无 LLM 依赖）

- [x] 2.1 service/JobProfileService（create/update/delete/getById/listAll/listByFilter/listByStatus/updateEmbedding）
- [x] 2.2 service/ResumeService（同上方法集）
- [x] 2.3 service/InterviewService（create/update/updateStatus/getById/listByJobId/listByResumeId/listAll/deleteByJobId/deleteByResumeId + addQuestion/batchAddQuestions/adoptQuestion/listQuestions/listAdoptedQuestions/getQuestionById）
- [x] 2.4 service/QuestionService（generateQuestions）
- [x] 2.5 service/UserService（create/update/delete/listAll/assignRoles）
- [x] 2.6 service/HrPreferenceService（getByHrId/save/cleanExpired）
- [x] 2.7 service/ExportService（exportSession）
- [x] 2.8 AgentTraceService 从 agent/event/ 迁到 service/（record/batchRecord/getSessionTrace/listByAgent/countByAgent/getAllSessionCount/getCompletedSessions/getSessionsWithToolCalls），更新引用方

## 3. 向量与分块 Service

- [x] 3.1 service/VectorSearchService 补全 searchCandidates（调 DocumentChunkMapper.searchByVector）
- [x] 3.2 service/DocumentChunkService（chunkAndEmbedJob/chunkAndEmbedResume）

## 4. 核心业务 Service（带 LLM）

- [x] 4.1 candidate/service/CandidateMatchService（四阶段 matchForJob + CRUD），从 CandidateMatchingTool 外迁四阶段逻辑
- [x] 4.2 service/InterviewAgentService（startInitialInterview/processAnswer/streamProcessAnswer/endInterview/getAssistSuggestion/getReport）
- [x] 4.3 service/ResumeAnalysisService（analyzeFull/compareResumes，用 6 POJO + FileParserUtil）
- [x] 4.4 service/OutreachService（全方法清单）
- [x] 4.5 service/EvaluationService（addSample/listActiveSamples/listSamples/runFullEvaluation/runEvaluationByCategory/getHistoryStats）
- [x] 4.6 service/JobAnalysisService（analyze）
- [x] 4.7 service/MemoryService（门面，聚合 PostgresLongTermMemory/RedisSessionMemory）

## 5. Tool 剥离业务逻辑

- [x] 5.1 CandidateMatchingTool：删除内联四阶段+Java cosine，改调 CandidateMatchService，仅 truncate
- [x] 5.2 JobAnalysisTool：剥离 analyzeJob 逻辑到 JobAnalysisService
- [x] 5.3 InterviewQuestionTool：剥离到 QuestionService/InterviewService
- [x] 5.4 InterviewAgentTool：剥离到 InterviewAgentService
- [x] 5.5 OutreachAgentTool：剥离到 OutreachService
- [x] 5.6 ResumeAnalysisTool：剥离到 ResumeAnalysisService
- [x] 5.7 ResumeSearchTool：剥离到 ResumeService（search 封装）
- [x] 5.8 验证 `grep -r "Mapper" agent/tool/` 无业务 SQL

## 6. Controller 改调 Service

- [x] 6.1 各 Controller 注入从 Mapper 改为对应 Service（端点路径暂不动，P2 处理）
- [x] 6.2 mvn compile 通过

## 7. 验证

- [x] 7.1 `mvn clean compile -DskipTests` 通过
- [x] 7.2 启动连远程 PG，ReAct 调 matchCandidates 经 CandidateMatchService 四阶段跑通
- [x] 7.3 `grep -r "Mapper" agent/tool/` 为空（除包装器必要）
- [x] 7.4 `openspec validate p1-service-layer` 通过
