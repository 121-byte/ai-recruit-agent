# agent-orchestration-completeness Specification

## Purpose
TBD - created by archiving change p4-diff-backfill. Update Purpose after archive.
## Requirements
### Requirement: 对话编排五路四路径完整
ConversationAgentService SHALL 完整支持五路分流（CHITCHAT/SINGLE_TOOL/COMPOSITE/BATCH_INDEPENDENT/HITL）与 ReAct/Supervisor/ReWOO/HITL 四路径；confirmHitl 真实恢复执行（接 ContextSnapshotService），explain 返回 trace 摘要，feedback 调 PreferenceLearningService。

#### Scenario: HITL 恢复
- **WHEN** POST /api/agent/chat/confirm 带 replyId
- **THEN** ConversationAgentService.confirmHitl 经 ContextSnapshotService 恢复快照并继续执行

#### Scenario: explain
- **WHEN** POST /api/agent/chat/explain
- **THEN** 返回 {steps, summary, model}（AgentTraceReadService + DeepSeek）

### Requirement: 两层意图识别
IntentRouter SHALL 两层：Embedding 阈值（高置信直返）+ LLM 兜底（低置信五分类），锚点动态池阈值与流程对齐原项目。

#### Scenario: 高置信直返
- **WHEN** 用户输入与锚点 cosine ≥ 阈值
- **THEN** 零 LLM 调用直接返回意图

