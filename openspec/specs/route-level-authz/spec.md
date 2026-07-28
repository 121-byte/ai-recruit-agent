# route-level-authz Specification

## Purpose
TBD - created by archiving change p3-security-layer. Update Purpose after archive.
## Requirements
### Requirement: 全局登录校验
SaTokenConfig SHALL 对 `/api/**` 启用登录校验，排除 `/api/auth/login` 与 `/api/health/**`。Mock 模式下仍可放开（仅 mock=false 时强制）。

#### Scenario: 未登录访问
- **WHEN** 无 token 访问 /api/**（非 login/health）
- **THEN** 返回 401

### Requirement: OPS 专属写操作
SaTokenConfig SHALL 校验：`/api/admin/**` 仅 OPS；`/api/jobs` POST 仅 OPS；`/api/jobs/*` PUT/DELETE 仅 OPS；`/api/resumes/*` DELETE 仅 OPS。

#### Scenario: 非 OPS 写岗位
- **WHEN** HR 角色用户 POST /api/jobs
- **THEN** 403（仅 OPS）

### Requirement: HR 专属招聘操作
SaTokenConfig SHALL 校验：`/api/matches/job/*/run` POST、`/api/matches/*/feedback` POST、`/api/interviews/*/questions/generate` POST、`/api/interviews/*/questions/*/adopt` PUT、`/api/interview-agent/interviews/*/start` POST、`/api/interview-agent/interviews/*/assist` POST、`/api/interview-agent/sessions/*/answer` POST、`/api/interview-agent/sessions/*/answer/stream` POST、`/api/interview-agent/sessions/*/end` POST、`/api/agent/chat/confirm` POST、`/api/evaluation/run*` POST、`/api/evaluation/samples` POST、`/api/evaluation/samples/*` DELETE——仅 HR。

#### Scenario: 非 HR 执行匹配
- **WHEN** OPS 角色用户 POST /api/matches/job/3/run
- **THEN** 403

### Requirement: SSE 订阅校验
`/api/events/subscribe/*` SHALL 校验：路径 userId 与当前登录 id 一致，不一致则需 `events:subscribe:all` 权限。

#### Scenario: 订阅他人
- **WHEN** 用户 1 订阅 /api/events/subscribe/2
- **THEN** 需 events:subscribe:all 权限，否则 403

