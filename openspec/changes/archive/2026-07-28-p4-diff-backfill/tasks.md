# Tasks: p4-diff-backfill

## 1. ConversationAgentService

- [x] 1.1 五路分流（CHITCHAT/SINGLE_TOOL/COMPOSITE/BATCH/HITL）完整性核对
- [x] 1.2 ReAct/Supervisor/ReWOO/HITL 四路径核对
- [x] 1.3 confirmHitl 接 ContextSnapshotService 真实恢复（非空壳）
- [x] 1.4 explain 端点（AgentTraceReadService + DeepSeek）
- [x] 1.5 feedback 调 PreferenceLearningService

## 2. AgentEventSseMapper

- [x] 2.1 28→14 事件映射补齐（核对所有 AgentEvent 子类）
- [x] 2.2 PII 脱敏覆盖 text delta
- [x] 2.3 PII 脱敏覆盖 tool_result delta（原项目漏，复刻补）

## 3. LLM 工具类

- [x] 3.1 JsonGuard: 补 extractJson + parseAndValidate + 非法内容检测
- [x] 3.2 QuickInfoExtractor: 抽取逻辑补全（技能词表/教育/工作年限）
- [x] 3.3 LangFuseTraceService: trace 上报逻辑补全
- [x] 3.4 FileParserUtil/DeepSeekModelService/EmbeddingService/RerankService 方法集核对

## 4. memory/*（8 文件）

- [x] 4.1 RedisSessionMemory 短期+渐进式压缩核对
- [x] 4.2 PostgresLongTermMemory 方法集核对
- [x] 4.3 memory_graph 图谱边核对
- [x] 4.4 HybridMemoryRetriever RRF k=60 + 时间衰减 + 重要性加权核对
- [x] 4.5 AutoMemoryExtractor 关键词预过滤+LLM 提取核对
- [x] 4.6 MemoryConsolidationAgent 7 步巩固核对
- [x] 4.7 ConsolidationScheduler 双触发≥10 核对
- [x] 4.8 MemoryDecayJob / MemoryForgettingService 双重遗忘核对

## 5. AgentTraceService 统一

- [x] 5.1 合并 agent/event/AgentTraceService（record）+ service/AgentTraceReadService（读）→ service/AgentTraceService
- [x] 5.2 更新 ConversationAgentService 等引用
- [x] 5.3 删除 agent/event/AgentTraceService 旧类

## 6. 其余同名文件核对

- [x] 6.1 IntentRouter 阈值（0.8）+ 锚点动态池
- [x] 6.2 SpecialistAgentFactory/SupervisorAgentService/ReWooExecutor/RecruitmentAgentService 4 专家装配核对
- [x] 6.3 ContextAssembler 记忆注入组装核对
- [x] 6.4 ConversationGuardrail/ReflexionMiddleware 注入检测/三维评估核对
- [x] 6.5 AgentEventPublisher/AgentTraceAspect 核对

## 7. 验证

- [x] 7.1 mvn clean compile 通过
- [x] 7.2 curl 对话流（chitchat/single/composite）不退化
- [x] 7.3 curl matchCandidates 四阶段不退化
- [x] 7.4 openspec validate p4-diff-backfill 通过
