## Why

复刻项目业务逻辑被下沉进 `agent/tool/*`（Tool 直接注入 Mapper 写业务 SQL），缺失原项目的 18 个业务 Service 层，导致分层架构偏离、Tool 职责过重、Controller 无 Service 可调。需按《复刻项目迁移对齐清单》第二部分新建 18 个 Service + 6 个 analysis POJO，把内联在 Tool 里的逻辑拆回 Service，Tool 只做参数校验+调 Service+结果摘要。这是 P2 Controller 端点对齐的前置依赖。

## What Changes

- **BREAKING** Tool 层（`agent/tool/*`）剥离业务逻辑：禁止 Tool 直接注入 Mapper 或写业务 SQL；Tool 仅做参数校验 + 调 Service + 结果 truncate。逐个审核 CandidateMatchingTool/JobAnalysisTool/InterviewQuestionTool/InterviewAgentTool/OutreachAgentTool/ResumeAnalysisTool，内联逻辑外迁到对应 Service。
- 新建 18 个业务 Service（按原项目方法清单）：
  - `candidate/service/CandidateMatchService`（四阶段匹配：create/update/getById/getByJobAndResume/listByJobId/listSortedByJob/matchForJob/feedback）
  - `service/InterviewAgentService`（AI 面试官：startInitialInterview/processAnswer/streamProcessAnswer/endInterview/getAssistSuggestion/getReport）
  - `service/ResumeAnalysisService`（analyzeFull/compareResumes）
  - `service/DocumentChunkService`（chunkAndEmbedJob/chunkAndEmbedResume）
  - `service/OutreachService`（create/batchCreate/batchUpdateStatus/confirmBatchSend/generateAndCreatePersonalized/generateAndCreateBatch/transitionStatus/funnelByJob/kanbanStats/countByStatus/listByStatus/listByJobId/listByBatchId/getById/updateStatus）
  - `service/EvaluationService`（addSample/listActiveSamples/listSamples/runFullEvaluation/runEvaluationByCategory/getHistoryStats）
  - `service/JobAnalysisService`（analyze）
  - `service/UserService`（create/update/delete/listAll/assignRoles）
  - `service/VectorSearchService`（P0 已建壳，本阶段补全 searchCandidates）
  - `service/InterviewService`（create/update/updateStatus/getById/listByJobId/listByResumeId/listAll/deleteByJobId/deleteByResumeId/addQuestion/batchAddQuestions/adoptQuestion/listQuestions/listAdoptedQuestions/getQuestionById）
  - `service/QuestionService`（generateQuestions）
  - `service/ExportService`（exportSession）
  - `service/JobProfileService`（create/update/delete/getById/listAll/listByFilter/listByStatus/updateEmbedding）
  - `service/ResumeService`（create/update/delete/getById/listAll/listByFilter/listByStatus/updateEmbedding）
  - `service/HrPreferenceService`（getByHrId/save/cleanExpired）
  - `service/AgentTraceService`（从 agent/event/ 归位到 service/：record/batchRecord/getSessionTrace/listByAgent/countByAgent/getAllSessionCount/getCompletedSessions/getSessionsWithToolCalls）
  - `service/HrPreferenceService`（同上）
  - `service/MemoryService`（记忆服务门面，封装长期/短期记忆统一访问）
- 新建 6 个 analysis POJO：`ComparisonResult`/`ImplicitInsights`/`PotentialAssessment`/`ResumeAnalysisResult`/`RiskAssessment`/`StructuredData`（getter/setter + toJsonNode + fromJson）。
- Controller 改为经 Service 不直连 Mapper（本阶段先让 Controller 能调到 Service，端点对齐细节属 P2）。

## Capabilities

### New Capabilities
- `candidate-matching`: 候选人匹配全流程四阶段服务（pgvector 召回+方向预过滤+条件性 rerank+LLM 三维评分+透明加权），由 CandidateMatchService 提供，Tool/Controller 经此调用。
- `interview-agent`: AI 面试官会话服务（启动初面/处理回答/流式回答/结束/辅助建议/报告）。
- `resume-analysis`: 简历全量解析与对比服务（analyzeFull/compareResumes + 6 个 analysis POJO 聚合结果）。
- `document-chunking`: 文档分块 + 向量入库服务（chunkAndEmbedJob/chunkAndEmbedResume）。
- `outreach-workflow`: 候选人触达/邀约工作流服务（批量/状态流转/漏斗看板）。
- `evaluation-framework`: 离线 RAG 评估服务（样本管理/全量评估/分类评估/历史统计）。
- `business-crud-services`: 岗位/简历/面试/面试题/用户/HR偏好/导出/链路追踪 等 CRUD 与领域服务集合。
- `service-layer-separation`: 分层架构约束——Tool 不直连 Mapper、Controller 不直连 Mapper、Service 注入 Mapper+LLM/Embedding/Rerank。

### Modified Capabilities
（无现有 specs，首次引入）

## Impact

- **代码**：新增 `service/` 下 18 个 Service + `candidate/service/CandidateMatchService` + `service/analysis/` 6 POJO；改造 8 个 Tool（剥离业务逻辑）；AgentTraceService 从 agent/event/ 迁到 service/。
- **依赖**：依赖 P0 的 Mapper 自定义查询与 VectorSearchService。
- **API**：本阶段不改对外路径（P2 处理），但 Controller 调用从 Mapper 改为 Service。
- **风险**：Tool 剥离逻辑后需保证 Agent 路径（ReAct/Supervisor 调 Tool）行为不变；CandidateMatchingTool 四阶段逻辑外迁到 CandidateMatchService 后 Tool 改为薄封装。
