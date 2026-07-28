# rerank-instruct-alignment Specification

## Purpose
TBD - created by archiving change memory-rag-alignment. Update Purpose after archive.
## Requirements
### Requirement: Rerank instruct 场景化引导
RerankService SHALL 请求体含 `instruct` 字段（"根据岗位需求，按技术技能匹配度和相关工作经验对候选人简历排序"）；文档截断 800 字；`documents.size()<=topN` 直接原序返回（少调一次 API）。

#### Scenario: instruct 注入
- **WHEN** rerank(query, documents, topN)
- **THEN** 请求体含 instruct 招聘场景文案

#### Scenario: 原序短路
- **WHEN** documents.size() <= topN
- **THEN** 不调 API，直接原序返回

### Requirement: Rerank 适配声明
RerankService SHALL 适配保留原生端点 `/api/v1/services/rerank/text-rerank/text-rerank` + 模型 `qwen3-vl-rerank`（compatible `/reranks` 实测 404 不可用），仅补 instruct；mock 字符重叠降级 MUST 保留并标注非参考行为。

#### Scenario: 端点保留
- **WHEN** rerank 调用
- **THEN** 走原生端点（非 /reranks），但含 instruct

