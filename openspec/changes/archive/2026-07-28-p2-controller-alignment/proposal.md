## Why

复刻 Controller 端点路径/方法/返回与原项目偏离约 40%，多个端点空返回、类名/基路径不一致，前端 `api/index.js` 依赖这些路径会 404。需按《复刻项目迁移对齐清单》§5 逐字对齐 10 个 Controller 的端点签名，并补齐 §4 缺失的 dto 包与 ContextSnapshotService。这是 P3 安全规则（SaTokenConfig 路由匹配依赖 Controller 路径）的前置。

## What Changes

- **BREAKING** Controller 基路径/类名/端点逐字对齐原项目：
  - `AgentController` → `AgentChatController`（`/api/agent`），补齐 10 端点：`POST /chat`、`/chat/stream`、`/chat/stop`、`/chat/confirm`（真实调 ConversationAgentService.confirmHitl 非空返回）、`/chat/feedback`（PreferenceLearningService）、`/chat/explain`（AgentTraceService+DeepSeek）、`POST /jobs/{jobId}/analyze`、`/jobs/{jobId}/match`、`/interviews/{interviewId}/questions`、`/sessions/{sessionId}/export`。`agentId="hr:"+StpUtil.getLoginIdAsLong()`。
  - `ChatSessionController` 基路径 `/api/chat/sessions` → `/api/agent/sessions`；补 `PUT /{id}/title`、`GET /tokens/summary`、`GET /{id}/tokens`。
  - `DashboardController` 补齐 8 端点：`/traces/session/{sessionId}`、`/traces/summary`、`/traces/tool-stats`、`/funnel`、`/outreach-kanban`、`/report-overview`、`/cost-summary/{sessionId}`、`/agent-metrics`。
  - `InterviewAgentController` 路径按 `{interviews|sessions}/{id}` 区分，补 `stream`/`end`/`assist`/`report` 共 6 端点。
  - `MatchController` → `CandidateMatchController`，`POST /{jobId}` → `POST /job/{jobId}/run`；补无参 POST、`GET /{id}`。
  - `InterviewController` 补 `PUT /{id}/status`、`/job/{jobId}`、`PUT /{interviewId}/questions/{questionId}/adopt`、`GET /{id}/stream`；`generate` 路径 `/questions/generate`。
  - `EvaluationController` 补 `POST /samples`、`GET /samples`、`POST /run/{category}`、`GET /history`。
  - `UserController` → `UserAdminController`，`/api/users` → `/api/admin/users`；补 `PUT /{id}`、`DELETE /{id}`、`PUT /{id}/roles`。
  - 恢复独立 `ResumeAnalysisController`（`/api/resumes`）：`POST /{resumeId}/analyze`、`POST /compare`。
  - `ResumeController` 补 `PUT /{id}`、`DELETE /{id}`；`JobController` 核对 list 的 status/title 过滤；`EventController` `GET /{userId}` → `GET /subscribe/{userId}` + 补 `GET /active`；`TaskController` → `TaskStatusController`，`/api/task/` → `/api/tasks/`；`AuthController` `/userinfo` → `/me`，返回 LoginResponse。
- 所有空返回端点接真实 Service（不返空）。
- 新建 dto 包：`CreateUserRequest`/`LoginRequest`/`LoginResponse`（含内部 `UserInfo`）/`UserDTO`。
- 新建 `agent/context/ContextSnapshotService`（上下文快照）。

## Capabilities

### New Capabilities
- `api-contract-alignment`: 对外 API 契约与原项目逐字一致——10 个 Controller 的路径/HTTP 方法/返回类型/类名/基路径对齐，无空返回端点。
- `dto-and-context-snapshot`: dto 请求/响应对象 + 上下文快照服务。

### Modified Capabilities
（无现有 specs，首次引入）

## Impact

- **代码**：14 个 Controller 重构/改名/补端点；新增 dto 包 4 类 + ContextSnapshotService。
- **依赖**：依赖 P1 的 18 个 Service（Controller 调 Service，端点补齐后调对应 Service 方法）。
- **API**：**BREAKING**——多个路径变化（`/api/chat/sessions`→`/api/agent/sessions`、`/api/users`→`/api/admin/users`、`/api/task/`→`/api/tasks/`、`/userinfo`→`/me`、MatchController 路径等）；前端 api/index.js 须同步（属 P5）。
- **风险**：路径变化破坏现有前端调用——P5 同步前端。
