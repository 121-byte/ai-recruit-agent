## Context

P0/P1 已对齐数据访问与 Service 层。本阶段逐文件 diff 原项目同名文件，把缩水的方法/逻辑回填。重点：ConversationAgentService（五路四路径 + confirm/explain/feedback）、SseMapper（28 事件 + PII 全覆盖）、JsonGuard、QuickInfoExtractor、LangFuse、memory/*。

## Goals / Non-Goals

**Goals**
- 同名文件方法集/逻辑与原项目对齐（diff 无功能性缺失）。
- SSE 28 事件映射 + PII 覆盖 tool_result。
- AgentTraceService 引用统一（消除 P1 双份并存）。

**Non-Goals**
- 不回退已修 bug（SSE 双重封装、pgvector 检索、AppProperties 绑定、Mock chat 等 P0/P1 修复保持）。
- 不改 Controller 路径（P2）。

## Decisions

### D1: diff 回填保留已修 bug
- 回填时若原项目某逻辑在复刻已被 P0/P1 修正（如 SseMapper 双重封装、CandidateMatch pgvector），保留复刻修正，只回填缺失方法。
- **理由**: 不回退已验证的修复。

### D2: AgentTraceService 统一
- 将 agent/event/AgentTraceService（record）与 service/AgentTraceReadService（读）合并为 service/AgentTraceService，更新 ConversationAgentService 等引用，删 agent/event/ 旧类。

### D3: confirmHitl 接 ContextSnapshotService
- confirmHitl 改为真实恢复（replyId→快照），非空壳。

## Risks / Trade-offs

- [Risk] 回填引入回归 → Mitigation: 每文件回填后 mvn compile + 现有 curl 验证（matchCandidates/对话流）不退化。
- [Risk] 原项目某逻辑依赖未对齐的依赖 → Mitigation: diff 时识别并记录为 Open Question。

## Migration Plan

1. ConversationAgentService 回填（confirm/explain/feedback + 五路核对）。
2. SseMapper 28 事件 + PII tool_result 覆盖。
3. JsonGuard/QuickInfoExtractor/LangFuse/FileParser 回填。
4. memory/* 8 文件 diff 回填。
5. AgentTraceService 统一引用。
6. 其余（SpecialistAgentFactory/SupervisorAgentService/ReWooExecutor/RecruitmentAgentService/ContextAssembler/middleware）核对。
7. mvn clean compile + 启动 + curl 对话流/matchCandidates 验证不退化。

## Open Questions

- 原项目 IntentRouter 阈值是 0.8（清单 §7 提到）vs 复刻 0.85——diff 时以原项目为准对齐。
