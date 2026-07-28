# evaluation-framework Specification

## Purpose
TBD - created by archiving change p1-service-layer. Update Purpose after archive.
## Requirements
### Requirement: 样本管理
EvaluationService SHALL 提供 addSample / listActiveSamples / listSamples / listSamplesByCategory，管理 evaluation_golden_sample。

#### Scenario: 新增金标样本
- **WHEN** 调用 addSample(category, input, expected, criteria)
- **THEN** insert evaluation_golden_sample

### Requirement: 离线评估执行
EvaluationService SHALL 提供 runFullEvaluation / runEvaluationByCategory：对样本集按五策略×五指标×三档K评估，写 evaluation_result，返回汇总。

#### Scenario: 全量评估
- **WHEN** 调用 runFullEvaluation()
- **THEN** 对所有 active sample 执行五策略 → 算 Recall@K/Precision@K/NDCG@K/MRR/HitRate@K（K=5/10/20）→ 写 evaluation_result → 返回统计

### Requirement: 历史统计
EvaluationService SHALL 提供 getHistoryStats 返回按分类的平均分等聚合。

#### Scenario: 历史聚合
- **WHEN** 调用 getHistoryStats()
- **THEN** 经 EvaluationResultMapper.avgScoreByCategory 聚合返回

