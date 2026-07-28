# test-suite Specification

## Purpose
TBD - created by archiving change p5-tests-frontend. Update Purpose after archive.
## Requirements
### Requirement: 7 个测试移植
系统 SHALL 移植 7 个测试到 `src/test/java/com/example/recruit/...`，`mvn test` 通过：SpecialistAgentFactorySecurityTest、AgentEventSseMapperSecurityTest、IntentEvalRunner、AutoMemoryExtractorInjectionTest、PermissionMappingTest、AuthIntegrationTest、AuthServiceTest。

#### Scenario: mvn test
- **WHEN** 执行 mvn test
- **THEN** 7 测试全绿（IntentEvalRunner 可 @Disabled 或 Mock 数据集）

### Requirement: Security 测试覆盖
SpecialistAgentFactorySecurityTest SHALL 验证 4 专家 Agent 装配安全（disable 危险能力）；AgentEventSseMapperSecurityTest SHALL 验证 PII 脱敏覆盖 text/tool_result；PermissionMappingTest SHALL 验证 HR/OPS 权限码合并去重；AutoMemoryExtractorInjectionTest SHALL 验证注入检测。

#### Scenario: PII 脱敏测试
- **WHEN** AgentEventSseMapperSecurityTest 对含手机号的 tool_result
- **THEN** 断言输出经脱敏

