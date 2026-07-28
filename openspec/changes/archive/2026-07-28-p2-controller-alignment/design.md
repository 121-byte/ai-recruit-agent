## Context

P1 已建 18 Service，Controller 已改调 Service 但端点路径/类名/方法签名与原项目偏离约 40%。本阶段按原项目同名 Controller 逐字对齐端点，补齐缺失端点与 dto。前端 api/index.js 依赖这些路径（P5 同步）。

## Goals / Non-Goals

**Goals**
- 10 个 Controller 端点路径/HTTP 方法/返回类型/类名/基路径与原项目逐字一致。
- 所有空返回端点接真实 Service。
- dto 包 4 类 + ContextSnapshotService。

**Non-Goals**
- 不改 Service 内部逻辑（P4 diff 回填）。
- 不实现 SaTokenConfig 路由规则（P3，依赖本阶段路径）。
- 不改前端（P5）。

## Decisions

### D1: Controller 改名 + 基路径对齐
- AgentController→AgentChatController；UserController→UserAdminController（/api/users→/api/admin/users）；TaskController→TaskStatusController（/api/task/→/api/tasks/）。
- **理由**: 与 SaTokenConfig 路由规则（P3）配对（/api/admin/** OPS）。

### D2: chat/confirm 接 ContextSnapshotService
- confirmHitl 调 ContextSnapshotService 按 replyId 恢复快照 → 真实恢复执行（非空壳）。
- **风险**: 快照存储位置（内存 Map vs Redis）——本阶段内存 Map（replyId→RuntimeContext），Redis 密码补后迁 Redis。

### D3: stream 端点统一 Flux<ServerSentEvent<String>>
- chat/stream 与 interview-agent sessions/{id}/answer/stream 均返回 Flux<ServerSentEvent<String>>，避免双重封装（P0 已修）。

## Risks / Trade-offs

- [Risk] 路径 BREAKING 破坏现有前端 → Mitigation: P5 同步 api/index.js；本阶段先保证后端契约对齐。
- [Risk] AgentChatController 合并 10 端点（含 /jobs/{jobId}/analyze、/sessions/{id}/export 等跨域端点）→ Mitigation: 原项目如此设计（Agent 对话入口聚合），照抄。

## Migration Plan

1. 新建 dto 包 + ContextSnapshotService。
2. AgentController→AgentChatController + 10 端点；ChatSessionController 基路径；Dashboard 8 端点。
3. InterviewAgent/CandidateMatch/Interview/Evaluation/UserAdmin/ResumeAnalysis 端点对齐。
4. Resume/Event/TaskStatus/Auth 收尾。
5. mvn compile + curl 各端点验证非 404、非空返回。

## Open Questions

- `/chat/explain` 调 AgentTraceService+DeepSeek 的具体输出结构？——决策：返回 {steps, summary, model}，原项目照抄。
