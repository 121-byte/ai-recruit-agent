# Tasks: p5-tests-frontend

## 1. 测试移植（7 个）

- [x] 1.1 PermissionMappingTest（security/）
- [x] 1.2 AuthServiceTest（service/，@MockBean 隔离 DB）
- [x] 1.3 AuthIntegrationTest（service/，@SpringBootTest + MockBean）
- [x] 1.4 SpecialistAgentFactorySecurityTest（agent/core/，验证 disable 危险能力）
- [x] 1.5 AgentEventSseMapperSecurityTest（agent/event/，PII 脱敏覆盖 text/tool_result）
- [x] 1.6 AutoMemoryExtractorInjectionTest（memory/，注入检测）
- [x] 1.7 IntentEvalRunner（agent/routing/，五策略 RAG 评估，@Disabled 或 Mock 数据集）

## 2. 测试验证

- [x] 2.1 mvn test 全绿
- [x] 2.2 无测试依赖真实 DB/Redis/LLM（@MockBean 隔离）

## 3. 前端 api/index.js 对齐

- [x] 3.1 会话路径 /api/agent/sessions
- [x] 3.2 用户路径 /api/admin/users
- [x] 3.3 任务路径 /api/tasks
- [x] 3.4 认证路径 /api/auth/me + LoginResponse
- [x] 3.5 匹配路径 /api/matches/job/{id}/run
- [x] 3.6 AI 面试路径 /api/interview-agent/{interviews|sessions}/{id}/*
- [x] 3.7 评估路径 /api/evaluation/samples
- [x] 3.8 其余端点对齐 P2

## 4. SSE 契约 + composable

- [x] 4.1 useAgentStream 核对 SSE 事件名与 AgentEventSseMapper 一致
- [x] 4.2 新增 useCompareTask
- [x] 4.3 新增 useMatchTask

## 5. 前端验证

- [ ] 5.1 npm run dev 启动
- [ ] 5.2 浏览器 smoke：登录→对话→匹配（非 404）
- [x] 5.3 openspec validate p5-tests-frontend 通过
