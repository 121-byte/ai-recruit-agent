## Why

复刻 0 个测试（原项目 7 个）+ 前端 `api/index.js` 路径未随 P2 Controller 对齐同步（会 404）+ 缺 `useCompareTask`/`useMatchTask` composable。需按《复刻项目迁移对齐清单》§8/§9 移植 7 个测试、同步前端 api 路径与 SSE 契约、补 composable。

## What Changes

- 移植 7 个测试到 `src/test/java/com/example/recruit/...`，`mvn test` 全绿：
  - `SpecialistAgentFactorySecurityTest`（agent/core/）
  - `AgentEventSseMapperSecurityTest`（agent/event/）
  - `IntentEvalRunner`（agent/routing/，五策略 RAG 评估）
  - `AutoMemoryExtractorInjectionTest`（memory/）
  - `PermissionMappingTest`（security/）
  - `AuthIntegrationTest`（service/）
  - `AuthServiceTest`（service/）
- 前端 `api/index.js` 全量对齐 P2 后端 API 路径（/api/agent/sessions、/api/admin/users、/api/tasks、/api/auth/me、/api/matches/job/{id}/run 等）。
- SSE 事件契约与 AgentEventSseMapper 一致（text/tool_call/tool_result/hitl/thinking/trace/done/error 等事件名）。
- 补 composable `useCompareTask`、`useMatchTask`；核对 `useAgentStream`。
- 状态管理：保留 store/auth.js + LoginView，确保与 /api/auth/login 对齐。
- 布局组件（MainLayout/SessionSidebar/StatsBar/SearchSources）保留，确保路由与后端一致。

## Capabilities

### New Capabilities
- `test-suite`: 7 个单元/集成测试覆盖安全/SseMapper/意图评估/记忆注入/权限/认证。
- `frontend-api-contract`: 前端 api/index.js 与后端 P2 路径全量对齐 + SSE 契约一致 + composable 补齐。

### Modified Capabilities
（无）

## Impact

- **代码**：新增 src/test/ 7 测试类；前端 api/index.js 重写路径 + 2 composable 新增。
- **依赖**：依赖 P2（Controller 路径）+ P3（PermissionMapping）+ P4（SseMapper 28 事件）。
- **风险**：测试依赖真实 DB/Redis/LLM——部分用 Mock 或 @MockBean；IntentEvalRunner 较重（31K），可标 @Disabled 或用 Mock 数据集。
