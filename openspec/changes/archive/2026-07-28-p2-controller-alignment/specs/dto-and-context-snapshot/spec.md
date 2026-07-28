## ADDED Requirements

### Requirement: dto 请求/响应对象
系统 SHALL 新建 dto 包：`CreateUserRequest`（username/password/realName/department）、`LoginRequest`（username/password）、`LoginResponse`（token + 内部 `UserInfo`：id/username/realName/email/phone/department/roles）、`UserDTO`（用户列表/详情响应，password 不出现）。

#### Scenario: 登录响应
- **WHEN** POST /api/auth/login 成功
- **THEN** 返回 LoginResponse（含 token 与 UserInfo），MUST NOT 返回 password

### Requirement: 上下文快照服务
系统 SHALL 新建 `agent/context/ContextSnapshotService`：捕获/恢复 RuntimeContext 快照（含 memorySnapshot / agentId / sessionId），供 HITL 确认后恢复执行。

#### Scenario: 快照恢复
- **WHEN** chat/confirm 带 replyId
- **THEN** ContextSnapshotService 按 replyId 恢复快照，ConversationAgentService.confirmHitl 据此恢复执行
