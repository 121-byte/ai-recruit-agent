# api-contract-alignment Specification

## Purpose
TBD - created by archiving change p2-controller-alignment. Update Purpose after archive.
## Requirements
### Requirement: AgentChatController 10 端点对齐
系统 SHALL 将 Agent 对话 Controller 命名为 `AgentChatController`（基路径 `/api/agent`），提供 10 个端点：POST `/chat`、`/chat/stream`、`/chat/stop`、`/chat/confirm`、`/chat/feedback`、`/chat/explain`、`/jobs/{jobId}/analyze`、`/jobs/{jobId}/match`、`/interviews/{interviewId}/questions`、`/sessions/{sessionId}/export`。

#### Scenario: chat/stream
- **WHEN** POST /api/agent/chat/stream
- **THEN** 返回 Flux<ServerSentEvent<String>>（经 ConversationAgentService.stream）

#### Scenario: chat/confirm 真实调用
- **WHEN** POST /api/agent/chat/confirm
- **THEN** 调 ConversationAgentService.confirmHitl，返回非空结果（含 replyId 归属），MUST NOT 空返回

#### Scenario: chat/stop
- **WHEN** POST /api/agent/chat/stop
- **THEN** 调 ConversationAgentService.stop，返回 {stopped:true}

### Requirement: 会话 Controller 基路径与端点
系统 SHALL 将会话 Controller 基路径设为 `/api/agent/sessions`，提供 GET 列表、POST 创建、DELETE `/{id}`、GET `/{id}/messages`、PUT `/{id}/title`、GET `/tokens/summary`、GET `/{id}/tokens`。

#### Scenario: 改基路径
- **WHEN** 访问会话端点
- **THEN** 路径以 /api/agent/sessions 开头（非 /api/chat/sessions）

### Requirement: Dashboard 8 端点
系统 SHALL 在 `/api/dashboard` 提供 8 端点：`/traces/session/{sessionId}`、`/traces/summary`、`/traces/tool-stats`、`/funnel`、`/outreach-kanban`、`/report-overview`、`/cost-summary/{sessionId}`、`/agent-metrics`。

#### Scenario: traces
- **WHEN** GET /api/dashboard/traces/session/{sessionId}
- **THEN** 调 AgentTraceReadService.getSessionTrace 返回 List

### Requirement: InterviewAgent 6 端点按 interviews/sessions 区分
系统 SHALL 在 `/api/interview-agent` 提供按 `{interviews|sessions}/{id}` 区分的 6 端点：`POST /interviews/{id}/start`、`POST /sessions/{id}/answer`、`POST /sessions/{id}/answer/stream`、`POST /sessions/{id}/end`、`POST /interviews/{id}/assist`、`GET /interviews/{id}/report`。

#### Scenario: stream answer
- **WHEN** POST /api/interview-agent/sessions/{id}/answer/stream
- **THEN** 返回 Flux<ServerSentEvent<String>>（InterviewAgentService.streamProcessAnswer）

### Requirement: CandidateMatch/Interview/Evaluation/UserAdmin/ResumeAnalysis 端点对齐
系统 SHALL：CandidateMatchController `POST /job/{jobId}/run`+`GET /job/{jobId}`+`GET /{id}`+`POST /{id}/feedback`+无参 POST；InterviewController 9 端点（含 `/job/{jobId}`、`PUT /{id}/status`、`PUT /{interviewId}/questions/{questionId}/adopt`、`GET /{id}/stream`、`generate` 路径 `/questions/generate`）；EvaluationController 5 端点（`/samples` 增查、`/run`、`/run/{category}`、`/history`）；UserAdminController `/api/admin/users` 5 端点（含 `PUT /{id}`、`DELETE /{id}`、`PUT /{id}/roles`）；ResumeAnalysisController 独立 `POST /{resumeId}/analyze`+`POST /compare`。

#### Scenario: 匹配执行路径
- **WHEN** POST /api/matches/job/{jobId}/run
- **THEN** 调 CandidateMatchService.matchForJob

#### Scenario: 简历对比
- **WHEN** POST /api/resumes/compare
- **THEN** 调 ResumeAnalysisService.compareResumes

### Requirement: 其余 Controller 端点对齐
系统 SHALL：ResumeController 补 `PUT /{id}`、`DELETE /{id}`；EventController `GET /subscribe/{userId}`+`GET /active`；TaskStatusController `/api/tasks/{taskId}/status`；AuthController `/login`+`/logout`+`/me`（返回 LoginResponse）。

#### Scenario: 当前用户
- **WHEN** GET /api/auth/me
- **THEN** 返回 LoginResponse（含 UserInfo），非 /userinfo

### Requirement: 无空返回端点
所有 Controller 端点 MUST 调真实 Service 返回数据，MUST NOT 返回固定空/占位。

#### Scenario: 端点接 Service
- **WHEN** 调用任一对齐端点
- **THEN** 返回来自 Service 的真实数据，无 "占位"/空对象

