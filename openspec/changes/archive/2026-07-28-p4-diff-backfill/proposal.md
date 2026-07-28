## Why

复刻多个同名文件实现缩水/偏离原项目（ConversationAgentService 16K vs 28K、IntentRouter 11.7K vs 14.6K、AgentEventSseMapper 8.9K vs 12.1K、JsonGuard 2K vs 4.8K、QuickInfoExtractor 3.3K vs 9.6K、LangFuseTraceService 2.7K vs 7.4K、memory/* 普遍偏小等）。需按《复刻项目迁移对齐清单》第七部分逐文件 diff，把缺失方法/逻辑从原项目回填。

## What Changes

- `agent/core/ConversationAgentService`：五路分流（CHITCHAT/SINGLE_TOOL/COMPOSITE/BATCH/HITL）+ ReAct/Supervisor/ReWOO 四路径完整性 + confirmHitl 真实恢复（接 P2 ContextSnapshotService）+ explain + feedback。
- `agent/routing/IntentRouter`：两层意图识别（Embedding 阈值 0.8 + LLM 兜底）+ 锚点动态池对齐原项目阈值与流程。
- `agent/event/AgentEventSseMapper`：28→14 事件映射补齐 + PII 脱敏覆盖 text/tool_result delta（原项目此处漏脱敏，复刻补上）。
- `llm/JsonGuard`：非法内容检测 + extractJson + parseAndValidate 完整。
- `llm/QuickInfoExtractor`：抽取逻辑补全（技能词表/教育/工作年限等）。
- `llm/LangFuseTraceService`：trace 上报逻辑补全。
- `llm/FileParserUtil`/`DeepSeekModelService`/`EmbeddingService`/`RerankService`：方法集核对对齐。
- `agent/core/{SpecialistAgentFactory,SupervisorAgentService,ReWooExecutor,RecruitmentAgentService}`：4 专家装配/Supervisor-Worker/ReWOO 三阶段核对。
- `agent/middleware/{ConversationGuardrail,ReflexionMiddleware}`：注入检测/Reflexion 三维评估核对。
- `memory/*`（8 个）：三层记忆完整性（Redis 短期/PG 长期/图谱/巩固/遗忘衰减+容量驱逐/混合检索 RRF）。
- `agent/context/ContextAssembler`：记忆注入组装核对。
- `agent/event/{AgentEventPublisher,AgentTraceAspect}`：事件发布/Trace 切面对齐（AgentTraceService 归位引用统一）。

## Capabilities

### New Capabilities
- `agent-orchestration-completeness`: 对话编排五路分流 + ReAct/Supervisor/ReWOO/HITL 四路径 + confirm/explain/feedback 完整。
- `llm-utils-completeness`: JsonGuard/QuickInfoExtractor/LangFuseTraceService/FileParser/DeepSeek/Embedding/Rerank 方法集与原项目一致。
- `memory-system-completeness`: 三层记忆 + 图谱 + 巩固 + 双重遗忘 + 混合检索 RRF 完整。

### Modified Capabilities
（无）

## Impact

- **代码**：~20 个同名文件 diff 回填缺失方法/逻辑。
- **依赖**：依赖 P1 Service 层（confirmHitl 接 ContextSnapshotService）、P2 Controller 路径。
- **风险**：回填时保持已有 P0/P1 验证过的行为不变（如 SSE 双重封装修复、pgvector 检索），不回退已修 bug。
