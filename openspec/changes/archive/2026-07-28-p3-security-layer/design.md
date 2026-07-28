## Context

P2 完成后 Controller 路径已对齐。本阶段补全安全层：路由级 RBAC（SaTokenConfig 按 HTTP 方法+路径粒度校验）、PermissionMapping 角色-权限码、HITL 5 检查点、SecurityConfig。

## Goals / Non-Goals

**Goals**
- SaTokenConfig 全路由规则（OPS 写/HR 招聘操作/SSE 自身订阅）。
- PermissionMapping + StpInterfaceImpl 桥接。
- RecruitmentPermissionService 5 HITL 检查点。
- SecurityConfig 新建。

**Non-Goals**
- 不实现附录的可选安全增强（多轮越狱检测/Canary 蜜标等，非复刻范围）。
- 不改 Controller 路径（P2 已对齐）。

## Decisions

### D1: SaRouter 按方法+路径粒度
- 用 `SaRouter.match(path).check(r -> StpUtil.checkRole("HR"))` + 在 check 内按 `SaHolder.getRequest().getMethod()` 区分 POST/PUT/DELETE。
- **理由**: 原项目如此（路径+方法双维度），与 P2 路径精确配对。

### D2: PermissionMapping 静态 Map
- HR/OPS 权限码硬编码静态 Map，getPermissions 合并去重。
- **理由**: 复刻原项目静态实现；权限码稳定。

### D3: HITL 检查点用 AgentScope PermissionContext
- RecruitmentPermissionService.init() 配置 allowRules/askRules 按 toolName。
- **风险**: AgentScope PermissionContext API（allowRules/askRules Map）需与 P0 核对的 PermissionContextState.builder 一致——照原项目 init() 抄。

## Risks / Trade-offs

- [Risk] 路由规则路径与 P2 Controller 不一致 → Mitigation: P2 先完成，本阶段路径照 P2 实际路径。
- [Risk] Mock 模式登录放开（P0 保留）与生产鉴权混用 → Mitigation: SaTokenConfig 仅 mock=false 时强制，mock=true 放开（与 P0 一致）。

## Migration Plan

1. PermissionMapping 新建 + StpInterfaceImpl 改调。
2. SaTokenConfig 全路由规则（路径照 P2）。
3. RecruitmentPermissionService 5 检查点。
4. SecurityConfig 新建。
5. ConversationGuardrail/AuthExceptionHandler 核对。
6. mvn compile + 启动 + curl 鉴权场景（401/403/200）验证。

## Open Questions

- AgentScope PermissionContext 的 allowRules/askRules 精确 API？——决策：照原项目 RecruitmentPermissionService.init()，P3 apply 时打开原项目同名文件照抄。
