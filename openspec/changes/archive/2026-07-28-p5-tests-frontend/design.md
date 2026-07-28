## Context

P2 路径对齐后前端 api/index.js 须同步；P3 PermissionMapping 须有测试；P4 SseMapper 28 事件须测试。原项目 7 测试复刻 0。本阶段移植测试 + 同步前端。

## Goals / Non-Goals

**Goals**
- 7 测试移植，mvn test 全绿。
- 前端 api/index.js 与 P2 路径全量对齐 + SSE 契约一致。
- 补 useCompareTask/useMatchTask。

**Non-Goals**
- 不实现 E2E 测试（仅单元/集成）。
- 不改后端（依赖 P2/P3/P4 完成）。

## Decisions

### D1: 测试用 @MockBean 隔离 DB/LLM
- Security/Mapper/Permission/Auth 测试用 @SpringBootTest + @MockBean 隔离真实 DB/Redis/LLM；IntentEvalRunner 用 Mock 数据集或 @Disabled。
- **理由**: 测试不依赖远程基础设施，CI 友好。

### D2: 前端 api/index.js 随 P2 重写
- 路径照 P2 对齐结果重写；保留 axios 拦截器（satoken header + 401 跳 login）。

### D3: composable 轻量
- useCompareTask/useMatchTask 封装对应 api 调用 + 响应式状态，不重复 useAgentStream 的 SSE 逻辑。

## Risks / Trade-offs

- [Risk] IntentEvalRunner 31K 重 → Mitigation: @Disabled 或简化数据集。
- [Risk] 测试依赖 Spring 上下文启动慢 → Mitigation: @SpringBootTest(webEnvironment=NONE) + MockBean。

## Migration Plan

1. 移植 7 测试（先 PermissionMappingTest/AuthServiceTest 简单的，再 Security/Injection，最后 IntentEvalRunner）。
2. mvn test 全绿。
3. 前端 api/index.js 重写路径。
4. useCompareTask/useMatchTask 新增。
5. useAgentStream 核对 SSE 事件。
6. 前端 npm run dev + 浏览器 smoke：登录→对话→匹配。

## Open Questions

- AuthIntegrationTest 是否需要真实 PG？——决策：用 @MockBean 隔离，不依赖真实 DB。
