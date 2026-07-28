# llm-utils-completeness Specification

## Purpose
TBD - created by archiving change p4-diff-backfill. Update Purpose after archive.
## Requirements
### Requirement: SSE 28→14 事件映射 + PII 全覆盖
AgentEventSseMapper SHALL 覆盖原项目 28 种 AgentEvent 子类映射，且 PII 脱敏 MUST 覆盖 text delta 与 tool_result delta（原项目 tool_result 漏脱敏，复刻补上）。

#### Scenario: tool_result PII 脱敏
- **WHEN** ToolResultTextDeltaEvent 含手机号
- **THEN** SSE 输出经 maskPii 脱敏

### Requirement: JsonGuard 完整
JsonGuard SHALL 提供 parseJsonSafe + extractJson + parseAndValidate + 非法内容检测。

#### Scenario: parseAndValidate
- **WHEN** parseAndValidate(text, schema)
- **THEN** 校验 JSON 合法性并返回结果

### Requirement: QuickInfoExtractor/LangFuse/FileParser/LLM 方法集对齐
QuickInfoExtractor SHALL 抽取姓名/电话/邮箱/技能/教育/工作年限；LangFuseTraceService SHALL trace 上报（model/prompt/response/tokens/latency）；FileParserUtil markitdown 优先 PDFBox/POI 兜底（P0 已对齐，核对）；DeepSeekModelService/EmbeddingService/RerankService 方法集与维度 1024 对齐。

#### Scenario: trace 上报
- **WHEN** LangFuse enabled 且 LLM 调用
- **THEN** 上报 model/prompt/response/tokens/latency 到 LangFuse

