package com.example.recruit.agent.routing;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import org.springframework.stereotype.Component;

/**
 * HITL 权限引擎 (复刻自文档 §7.3 RecruitmentPermissionService)。
 *
 * <p>构建 AgentScope {@link PermissionContextState}，定义工具执行权限。
 * 实际角色-工具映射由 Sa-Token RBAC 在 Controller 层拦截，此处提供 AgentScope 运行时权限上下文。
 *
 * <p>权限映射 (文档 §7.3 security/PermissionMapping.java)：
 * <table>
 *   <tr><th>工具</th><th>角色</th><th>HITL</th></tr>
 *   <tr><td>matchCandidates</td><td>HR</td><td>否</td></tr>
 *   <tr><td>generateQuestions</td><td>HR, OPS</td><td>否</td></tr>
 *   <tr><td>generateOutreach</td><td>HR</td><td>否</td></tr>
 *   <tr><td>startInterview</td><td>HR</td><td>否</td></tr>
 *   <tr><td>analyzeJob</td><td>HR, OPS</td><td>否</td></tr>
 *   <tr><td>searchResumes</td><td>HR, OPS</td><td>否</td></tr>
 *   <tr><td>webSearch</td><td>HR, OPS</td><td>否</td></tr>
 * </table>
 */
@Component
public class RecruitmentPermissionService {

    private static final PermissionContextState CONTEXT =
            PermissionContextState.builder().mode(PermissionMode.DEFAULT).build();

    /**
     * 返回 AgentScope 运行时权限上下文。
     * 使用 DEFAULT 模式：工具调用无需逐次确认 (HR 的敏感操作由 Sa-Token 角色网关保护)。
     */
    public PermissionContextState getContext() {
        return CONTEXT;
    }
}
