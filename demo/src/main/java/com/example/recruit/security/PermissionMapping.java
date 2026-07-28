package com.example.recruit.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色-权限码映射 (复刻自文档 §7.2 PermissionMapping)。
 *
 * <p>HR/OPS 角色 → 权限码静态映射，{@link #getPermissions(List)} 去重合并。
 * 供 {@link StpInterfaceImpl#getPermissionList} 调用，桥接 Sa-Token 权限体系。
 */
@Component
public class PermissionMapping {

    private static final Map<String, List<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        List<String> hrPermissions = List.of(
                "resume:analyze", "resume:compare",
                "job:read", "job:analyze",
                "match:run", "match:read", "match:feedback",
                "interview:create", "interview:question:generate",
                "interview:question:read", "interview:question:adopt",
                "interview-agent:start", "interview-agent:assist",
                "interview-agent:report", "interview-agent:report:read",
                "outreach:approve", "outreach:send",
                "agent:chat", "agent:hitl:confirm", "agent:feedback",
                "dashboard:view",
                "evaluation:manage", "evaluation:run", "evaluation:read",
                "events:subscribe");
        ROLE_PERMISSIONS.put("HR", hrPermissions);

        List<String> opsPermissions = List.of(
                "resume:upload", "resume:analyze", "resume:compare",
                "job:create", "job:update", "job:delete", "job:read",
                "match:read",
                "interview:create", "interview:question:read",
                "interview-agent:report:read",
                "outreach:send",
                "agent:chat",
                "dashboard:view",
                "evaluation:read",
                "events:subscribe",
                "user:manage");
        ROLE_PERMISSIONS.put("OPS", opsPermissions);
    }

    /**
     * 按角色 code 列表合并去重权限码。
     */
    public static List<String> getPermissions(List<String> roleCodes) {
        Set<String> merged = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String role : roleCodes) {
                List<String> perms = ROLE_PERMISSIONS.get(role);
                if (perms != null) {
                    merged.addAll(perms);
                }
            }
        }
        return new ArrayList<>(merged);
    }
}
