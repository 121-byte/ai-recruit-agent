# service-layer-separation Specification

## Purpose
TBD - created by archiving change p1-service-layer. Update Purpose after archive.
## Requirements
### Requirement: Tool 不直连 Mapper
agent/tool/* 下的 @Tool 类 MUST NOT 注入 Mapper 或写业务 SQL，SHALL 只做：参数校验 + 调 Service + 结果 truncate（避免撑爆 ReAct 上下文）。

#### Scenario: Tool 调 Service
- **WHEN** CandidateMatchingTool.matchCandidates(jobId) 被调用
- **THEN** Tool 不注入 Mapper，转调 CandidateMatchService.matchForJob(jobId)，仅 truncate 结果

#### Scenario: 全项目无 Tool 直连 Mapper
- **WHEN** 执行 `grep -r "Mapper" agent/tool/`
- **THEN** 无业务 SQL 或 Mapper 注入（除必要包装器无）

### Requirement: Controller 不直连 Mapper
controller/* MUST 经 Service 访问数据，MUST NOT 直接注入 Mapper。

#### Scenario: Controller 调 Service
- **WHEN** Controller 处理请求
- **THEN** 注入 Service 而非 Mapper，数据访问经 Service

### Requirement: Service 注入 Mapper + LLM/Embedding/Rerank
Service 层 SHALL 注入对应 Mapper + 必要的 DeepSeekModelService/EmbeddingService/RerankService，承载业务逻辑。

#### Scenario: Service 依赖注入
- **WHEN** CandidateMatchService 构造
- **THEN** 注入 ResumeMapper/JobProfileMapper/CandidateMatchMapper + VectorSearchService + RerankService + DeepSeekModelService

### Requirement: AgentTraceService 归位
AgentTraceService SHALL 位于 `service/` 包（原复刻误置于 `agent/event/`），保持 record 等方法签名。

#### Scenario: 归位
- **WHEN** 检查包路径
- **THEN** AgentTraceService 在 com.example.recruit.service（agent/event/ 不再有该类）

