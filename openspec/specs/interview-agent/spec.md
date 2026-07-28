# interview-agent Specification

## Purpose
TBD - created by archiving change p1-service-layer. Update Purpose after archive.
## Requirements
### Requirement: AI 面试官会话生命周期
InterviewAgentService SHALL 提供 startInitialInterview / processAnswer / streamProcessAnswer / endInterview / getAssistSuggestion / getReport，管理 interview_session 与 interview_report。

#### Scenario: 启动初面
- **WHEN** 调用 startInitialInterview(interviewId)
- **THEN** 创建/取 interview_session，调 DeepSeek 生成开场白+第一题，写 messages JSONB，返回 session

#### Scenario: 流式回答
- **WHEN** 调用 streamProcessAnswer(sessionId, answer)
- **THEN** 调 DeepSeekModelService.chatStream 流式返回评估+追问/下一题，Flux<String> SSE 帧

#### Scenario: 生成报告
- **WHEN** 调用 getReport(interviewId)
- **THEN** 调 DeepSeek 生成四维评分(tech/comm/problem_solving/culture_fit)+strengths/risks+hiring_suggestion，写 interview_report

### Requirement: 难度调整
InterviewAgentService SHALL 支持实时难度调整（medium 默认）。

#### Scenario: 切换难度
- **WHEN** processAnswer 带 difficultyLevel 参数
- **THEN** 更新 session.difficulty_level，后续出题难度随之变化

