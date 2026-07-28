# Tasks: p2-controller-alignment

## 1. dto + ContextSnapshotService

- [x] 1.1 新建 dto/CreateUserRequest
- [x] 1.2 新建 dto/LoginRequest
- [x] 1.3 新建 dto/LoginResponse（含内部 UserInfo）
- [x] 1.4 新建 dto/UserDTO
- [x] 1.5 新建 agent/context/ContextSnapshotService（replyId→RuntimeContext 内存 Map + 恢复）

## 2. AgentChatController（改名 + 10 端点）

- [x] 2.1 AgentController → AgentChatController（@RequestMapping /api/agent）
- [x] 2.2 POST /chat（非流式，RecruitmentAgentService.chat）
- [x] 2.3 POST /chat/stream（Flux<ServerSentEvent>，已存在，核对）
- [x] 2.4 POST /chat/stop（ConversationAgentService.stop）
- [x] 2.5 POST /chat/confirm（ConversationAgentService.confirmHitl + ContextSnapshotService，非空）
- [x] 2.6 POST /chat/feedback（PreferenceLearningService.processFeedback）
- [x] 2.7 POST /chat/explain（AgentTraceReadService + DeepSeek）
- [x] 2.8 POST /jobs/{jobId}/analyze（JobAnalysisService.analyze）
- [x] 2.9 POST /jobs/{jobId}/match（CandidateMatchService.matchForJob）
- [x] 2.10 POST /interviews/{interviewId}/questions（QuestionService.generateQuestions）
- [x] 2.11 POST /sessions/{sessionId}/export（ExportService.exportSession）
- [x] 2.12 agentId 统一 "hr:"+StpUtil.getLoginIdAsLong()

## 3. ChatSessionController 基路径 + 补端点

- [x] 3.1 基路径 /api/chat/sessions → /api/agent/sessions
- [x] 3.2 补 PUT /{id}/title
- [x] 3.3 补 GET /tokens/summary
- [x] 3.4 补 GET /{id}/tokens

## 4. Dashboard 8 端点

- [x] 4.1 /traces/session/{sessionId}
- [x] 4.2 /traces/summary
- [x] 4.3 /traces/tool-stats
- [x] 4.4 /funnel
- [x] 4.5 /outreach-kanban
- [x] 4.6 /report-overview
- [x] 4.7 /cost-summary/{sessionId}
- [x] 4.8 /agent-metrics

## 5. InterviewAgent 6 端点

- [x] 5.1 POST /interviews/{id}/start
- [x] 5.2 POST /sessions/{id}/answer
- [x] 5.3 POST /sessions/{id}/answer/stream（Flux<ServerSentEvent>）
- [x] 5.4 POST /sessions/{id}/end
- [x] 5.5 POST /interviews/{id}/assist
- [x] 5.6 GET /interviews/{id}/report

## 6. CandidateMatch/Interview/Evaluation/UserAdmin/ResumeAnalysis

- [x] 6.1 MatchController→CandidateMatchController，POST /{jobId}→POST /job/{jobId}/run，补 GET /{id}、无参 POST
- [x] 6.2 InterviewController 补 PUT /{id}/status、/job/{jobId}、PUT /{interviewId}/questions/{questionId}/adopt、GET /{id}/stream，generate→/questions/generate
- [x] 6.3 EvaluationController 补 POST /samples、GET /samples、POST /run/{category}、GET /history
- [x] 6.4 UserController→UserAdminController /api/admin/users，补 PUT /{id}、DELETE /{id}、PUT /{id}/roles
- [x] 6.5 恢复独立 ResumeAnalysisController：POST /{resumeId}/analyze、POST /compare

## 7. 其余 Controller

- [x] 7.1 ResumeController 补 PUT /{id}、DELETE /{id}
- [x] 7.2 JobController 核对 list status/title 过滤
- [x] 7.3 EventController GET /{userId}→GET /subscribe/{userId} + GET /active
- [x] 7.4 TaskController→TaskStatusController，/api/task/→/api/tasks/
- [x] 7.5 AuthController /userinfo→/me，返回 LoginResponse

## 8. 验证

- [x] 8.1 mvn clean compile 通过
- [x] 8.2 curl 各对齐端点非 404、非空返回
- [x] 8.3 openspec validate p2-controller-alignment 通过
