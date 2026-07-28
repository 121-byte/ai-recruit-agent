# permission-mapping Specification

## Purpose
TBD - created by archiving change p3-security-layer. Update Purpose after archive.
## Requirements
### Requirement: 角色-权限映射
PermissionMapping SHALL 维护 HR/OPS→权限码静态映射：HR 含 resume:analyze/compare、job:read/analyze、match:run/read/feedback、interview:* / interview-agent:* / outreach:approve/send、agent:chat/hitl:confirm/feedback、dashboard:view、evaluation:manage/run/read、events:subscribe 等；OPS 含 resume:upload/analyze/compare、job:create/update/delete/read、match:read、interview:create/question:read、user:manage 等。`getPermissions(roleCodes)` 去重合并。

#### Scenario: 合并角色权限
- **WHEN** getPermissions(["HR","OPS"])
- **THEN** 返回两角色权限码并集去重

### Requirement: Sa-Token permission/role 桥接
StpInterfaceImpl.getPermissionList SHALL 调 PermissionMapping.getPermissions(roleCodes)；getRoleList SHALL 调 SysRoleMapper.selectRoleCodesByUserId。

#### Scenario: 取当前用户权限
- **WHEN** StpUtil.getPermissionList(loginId)
- **THEN** 经 PermissionMapping 返回权限码列表

