# hitl-permission-checkpoints Specification

## Purpose
TBD - created by archiving change p3-security-layer. Update Purpose after archive.
## Requirements
### Requirement: 5 个 HITL 检查点
RecruitmentPermissionService SHALL 在 AgentScope PermissionContext 配置 5 检查点：JobAnalysisTool 所有方法 ALLOW；CandidateMatchingTool.matchCandidates ASK、getMatches ALLOW、feedback ALLOW；InterviewQuestionTool.generateQuestions ASK、adoptQuestion ASK、getQuestions ALLOW。

#### Scenario: matchCandidates 触发 ASK
- **WHEN** Agent 调 matchCandidates
- **THEN** PermissionContext 返回 ASK（需 HR 确认）触发 HITL

#### Scenario: getQuestions 直放行
- **WHEN** Agent 调 getQuestions
- **THEN** PermissionContext ALLOW，不触发 HITL

