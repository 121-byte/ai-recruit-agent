# Tasks: p3-security-layer

## 1. PermissionMapping + StpInterfaceImpl

- [x] 1.1 新建 security/PermissionMapping（HR/OPS→权限码静态 Map + getPermissions 去重合并）
- [x] 1.2 StpInterfaceImpl.getPermissionList 改调 PermissionMapping.getPermissions
- [x] 1.3 StpInterfaceImpl.getRoleList 改调 SysRoleMapper.selectRoleCodesByUserId

## 2. SaTokenConfig 全路由规则

- [x] 2.1 全局登录校验（/api/** 排除 login/health）
- [x] 2.2 /api/admin/** 仅 OPS
- [x] 2.3 岗位写操作（/api/jobs POST、/api/jobs/* PUT/DELETE）仅 OPS
- [x] 2.4 简历删除 /api/resumes/* DELETE 仅 OPS
- [x] 2.5 匹配执行/反馈 /api/matches/job/*/run、/api/matches/*/feedback 仅 HR
- [x] 2.6 面试题生成/采纳 /api/interviews/*/questions/generate、/api/interviews/*/questions/*/adopt 仅 HR
- [x] 2.7 AI 面试 start/assist/answer/answer/stream/end 仅 HR
- [x] 2.8 HITL confirm /api/agent/chat/confirm 仅 HR
- [x] 2.9 评估 run*/samples 仅 HR
- [x] 2.10 SSE /api/events/subscribe/* 校验订阅自身或 events:subscribe:all

## 3. RecruitmentPermissionService 5 检查点

- [x] 3.1 JobAnalysisTool 所有方法 ALLOW
- [x] 3.2 CandidateMatchingTool: matchCandidates ASK / getMatches ALLOW / feedback ALLOW
- [x] 3.3 InterviewQuestionTool: generateQuestions ASK / adoptQuestion ASK / getQuestions ALLOW

## 4. 其他安全类

- [x] 4.1 新建 config/SecurityConfig
- [x] 4.2 ConversationGuardrail 核对 PROMPT_INJECTION/BIAS_INPUT + V4-Flash 兜底
- [x] 4.3 AuthExceptionHandler 异常→HTTP 映射核对

## 5. 验证

- [x] 5.1 mvn clean compile 通过
- [x] 5.2 curl 鉴权场景：未登录 401、HR 写岗位 403、OPS 匹配 403、HR 匹配 200
- [x] 5.3 openspec validate p3-security-layer 通过
