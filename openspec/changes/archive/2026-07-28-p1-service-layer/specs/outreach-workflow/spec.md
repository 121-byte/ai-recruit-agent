## ADDED Requirements

### Requirement: 邀约工作流
OutreachService SHALL 提供批量生成、状态流转（draft→sent→replied→ignored）、漏斗看板统计能力：create/batchCreate/batchUpdateStatus/confirmBatchSend/generateAndCreatePersonalized/generateAndCreateBatch/transitionStatus/funnelByJob/kanbanStats/countByStatus/listByStatus/listByJobId/listByBatchId/getById/updateStatus。

#### Scenario: 批量生成个性化邀约
- **WHEN** 调用 generateAndCreateBatch(jobId, resumeIds)
- **THEN** 对每个候选人调 DeepSeek 生成个性化消息 → 批量 insert outreach（status=draft）

#### Scenario: 状态流转
- **WHEN** 调用 transitionStatus(id, fromStatus, toStatus)
- **THEN** 校验当前状态==fromStatus 后更新为 toStatus，状态非法流转拒绝

#### Scenario: 漏斗看板
- **WHEN** 调用 kanbanStats(jobId)
- **THEN** 返回各状态计数 {draft,sent,replied,ignored}

### Requirement: 批量更新
OutreachService SHALL 支持 batchUpdateStatus(batchId, newStatus) 批量改状态。

#### Scenario: 批量改状态
- **WHEN** 调用 batchUpdateStatus(batchId, status)
- **THEN** 经 OutreachMapper.batchUpdateStatus 批量 UPDATE
