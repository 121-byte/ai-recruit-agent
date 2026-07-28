## Why

复刻 SaTokenConfig 仅 1.5K，缺失全部路由级权限规则；无 PermissionMapping；RecruitmentPermissionService 仅 1.6K 缺 5 个 HITL 检查点。需按《复刻项目迁移对齐清单》§7 补全安全层：SaTokenConfig 全路由规则、PermissionMapping 角色-权限映射、RecruitmentPermissionService 5 HITL 检查点、SecurityConfig。依赖 P2 的 Controller 路径（路由规则路径须与 Controller 一致）。

## What Changes

- **SaTokenConfig 全路由规则**（照原项目 §7.1）：全局登录校验（排除 /api/auth/login、/api/health/**）；`/api/admin/**` 仅 OPS；岗位写操作（/api/jobs POST、/api/jobs/* PUT/DELETE）仅 OPS；简历删除 `/api/resumes/*` DELETE 仅 OPS；匹配执行/反馈 `/api/matches/job/*/run`、`/api/matches/*/feedback` 仅 HR；面试题生成/采纳仅 HR；AI 面试 start/assist/answer/answer/stream/end 仅 HR；HITL confirm `/api/agent/chat/confirm` 仅 HR；评估 run/samples 仅 HR；SSE 订阅 `/api/events/subscribe/*` 校验订阅自身或 events:subscribe:all 权限。
- **PermissionMapping**（§7.2）：HR/OPS 角色→权限码映射，`getPermissions(roleCodes)` 去重合并；StpInterfaceImpl.getPermissionList 调 PermissionMapping.getPermissions，getRoleList 调 SysRoleMapper.selectRoleCodesByUserId。
- **RecruitmentPermissionService 5 HITL 检查点**（§7.3）：JobAnalysisTool ALLOW、CandidateMatchingTool.matchCandidates ASK / getMatches ALLOW / feedback ALLOW、InterviewQuestionTool.generateQuestions ASK / adoptQuestion ASK / getQuestions ALLOW。
- **SecurityConfig**（§7.4）：新建（过滤器/Security 链）。
- ConversationGuardrail 核对 PROMPT_INJECTION/BIAS_INPUT + V4-Flash 兜底完整性；AuthExceptionHandler 异常→HTTP 映射核对。

## Capabilities

### New Capabilities
- `route-level-authz`: 路由级 RBAC 鉴权——SaTokenConfig 按 HTTP 方法+路径粒度校验角色（OPS/HR）与登录。
- `permission-mapping`: 角色-权限码映射（PermissionMapping）+ Sa-Token permission/role 桥接。
- `hitl-permission-checkpoints`: AgentScope PermissionContext 的 5 个 HITL 检查点（ASK/ALLOW）。

### Modified Capabilities
（无）

## Impact

- **代码**：SaTokenConfig 重写（全规则）、新建 PermissionMapping + SecurityConfig、RecruitmentPermissionService 补 5 检查点、StpInterfaceImpl 改调 PermissionMapping。
- **依赖**：依赖 P2 Controller 路径（路由规则路径须与 Controller 实际路径完全一致）。
- **风险**：路由规则路径与 Controller 不一致会漏校验/误拦截——P2 必须先完成。
