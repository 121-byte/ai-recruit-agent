## Context

复刻项目把业务逻辑内联进 Tool（Tool 直连 Mapper 写 SQL），缺少原项目的 18 个业务 Service。本阶段按原项目方法清单新建 Service + 6 analysis POJO，把 Tool 内联逻辑外迁，恢复 Service→Tool→Controller 分层。依赖 P0 的 Mapper 自定义查询与 VectorSearchService 壳。

## Goals / Non-Goals

**Goals**
- 18 个 Service 齐备（方法清单与原项目一致）。
- 6 个 analysis POJO（fromJson/toJson/toJsonNode）。
- Tool 剥离业务逻辑、不注入 Mapper；Controller 经 Service。
- AgentTraceService 归位到 service/。

**Non-Goals**
- 不改对外 API 路径/方法（属 P2）。
- 不移植 7 个测试（属 P5）。
- 不改 Agent 核心（ConversationAgentService 等内部逻辑 diff 回填属 P4）。

## Decisions

### D1: Service 用构造器注入 + @Service/@Transactional
- **选择**: 与复刻现有风格一致（构造器注入），需事务的（CandidateMatchService.matchForJob、OutreachService.batchCreate、MemoryConsolidationAgent）加 @Transactional。
- **理由**: 一致性，Spring Boot 3 推荐。

### D2: CandidateMatchingTool 四阶段逻辑整体外迁
- **选择**: 四阶段（召回/rerank/LLM评分/加权）整体移入 CandidateMatchService.matchForJob；Tool.matchCandidates 改为薄封装调 Service + truncate。
- **理由**: 清单第二部分要求 Tool 不写业务 SQL；四阶段是业务逻辑非 Agent 逻辑。
- **约束**: VectorSearchService.searchCandidates 作为召回入口被 CandidateMatchService 调用。

### D3: InterviewAgentService 流式用 ServerSentEvent
- **选择**: streamProcessAnswer 返回 `Flux<ServerSentEvent<String>>`，复用 AgentEventSseMapper 的帧格式或直接 chatStream。
- **替代**: 返回 Flux<String> —— 拒绝，需配合 P2 的 stream 端点避免双重封装（与 AgentController 一致用 ServerSentEvent）。

### D4: analysis POJO 简化实现
- **选择**: 6 POJO 用 Lombok @Data + 静态 fromJson(JsonNode)/toJson()/toJsonNode()；原项目可能更重，但复刻保留 Lombok 风格，方法集对齐即可。

## Risks / Trade-offs

- [Risk] Tool 剥离后 Agent 路径行为漂移 → Mitigation: 保持 Tool 方法签名不变（@Tool name/description 不变），内部改调 Service；逐个 Tool 验证 ReAct 调用链。
- [Risk] 18 Service 工作量大 → Mitigation: 按依赖分批，先建无 LLM 依赖的 CRUD Service，再建 CandidateMatchService/InterviewAgentService/ResumeAnalysisService/EvaluationService。
- [Risk] ResumeAnalysisService 依赖 FileParserUtil（markitdown） → Mitigation: markitdown 不可用时 PDFBox 兜底（P0 已处理）。

## Migration Plan

1. 建 6 个 analysis POJO。
2. 建 CRUD Service（JobProfile/Resume/Interview/Question/User/HrPreference/Export/AgentTraceService 归位）。
3. 建 DocumentChunkService（依赖 P0 searchByVector）。
4. 建 CandidateMatchService（四阶段，外迁 Tool 逻辑）。
5. 建 InterviewAgentService / ResumeAnalysisService / OutreachService / EvaluationService / JobAnalysisService / VectorSearchService 补全。
6. 改造 8 个 Tool 剥离业务逻辑改调 Service。
7. Controller 注入从 Mapper 改为 Service。
8. `grep -r "Mapper" agent/tool/` 为空验证；mvn compile + 启动验证。
9. 回滚: git revert。

## Open Questions

- MemoryService 门面与现有 PostgresLongTermMemory/RedisSessionMemory 关系？——决策：MemoryService 聚合两者，提供 get/save/search 统一入口，不替换底层。
