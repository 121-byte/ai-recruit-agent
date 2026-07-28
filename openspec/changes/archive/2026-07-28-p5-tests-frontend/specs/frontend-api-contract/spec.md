## ADDED Requirements

### Requirement: 前端 API 路径对齐后端
frontend/src/api/index.js SHALL 全量对齐 P2 后端路径：会话 /api/agent/sessions、用户 /api/admin/users、任务 /api/tasks、认证 /api/auth/me、匹配 /api/matches/job/{id}/run、AI 面试 /api/interview-agent/{interviews|sessions}/{id}/*、评估 /api/evaluation/samples 等。所有前端调用不返 404。

#### Scenario: 路径对齐
- **WHEN** 前端调任意 api 方法
- **THEN** 命中 P2 对齐的后端路径，非 404

### Requirement: SSE 事件契约一致
useAgentStream 解析的 SSE 事件名 SHALL 与 AgentEventSseMapper 输出一致（session/thinking/text/tool_call/tool_result/plan/hitl/trace/push/stats/error/done/stop）。

#### Scenario: 事件解析
- **WHEN** 后端推送 event: tool_result
- **THEN** useAgentStream.handleEvent 正确处理

### Requirement: composable 补齐
系统 SHALL 新增 `useCompareTask`（简历对比任务）、`useMatchTask`（匹配任务）composable；`useAgentStream` 核对与 SSE 契约一致。

#### Scenario: useMatchTask
- **WHEN** 调用 useMatchTask.run(jobId)
- **THEN** 调 POST /api/matches/job/{jobId}/run 并展示结果
