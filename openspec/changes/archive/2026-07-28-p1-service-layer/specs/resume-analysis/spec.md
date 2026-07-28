## ADDED Requirements

### Requirement: 简历全量解析
ResumeAnalysisService SHALL 提供 analyzeFull(resumeId)：用 LLM 解析 raw_text 为结构化数据（姓名/意向/技能/工作经历/教育/隐性洞察/潜力评估/风险评估），聚合为 ResumeAnalysisResult POJO，写回 parsed_json + embedding。

#### Scenario: 全量解析
- **WHEN** 调用 analyzeFull(resumeId)
- **THEN** FileParserUtil 解析文件（markitdown 优先）→ DeepSeek 结构化 → 聚合 6 个 analysis POJO → 写 parsed_json + 算 embedding + 更新 status=reviewed

### Requirement: 简历对比
ResumeAnalysisService SHALL 提供 compareResumes(resumeIds)：多简历横向对比，返回 ComparisonResult。

#### Scenario: 对比
- **WHEN** 调用 compareResumes([id1,id2,...])
- **THEN** 调 DeepSeek 对比维度 → 返回 ComparisonResult toJsonNode

### Requirement: analysis POJO 序列化
6 个 POJO（StructuredData/ImplicitInsights/PotentialAssessment/RiskAssessment/ComparisonResult/ResumeAnalysisResult）SHALL 提供 fromJson / toJson / toJsonNode 以支持 JSONB 持久化。

#### Scenario: 往返序列化
- **WHEN** POJO.fromJson(node).toJsonNode()
- **THEN** 与原 JSON 等价（无字段丢失）
