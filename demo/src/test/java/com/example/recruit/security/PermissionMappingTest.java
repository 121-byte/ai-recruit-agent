package com.example.recruit.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PermissionMapping} 角色-权限映射单元测试 (OpenSpec p5-tests §1)。
 *
 * <p>纯单元测试，无 Spring 上下文，验证：
 * <ul>
 *   <li>HR 角色含 match:run</li>
 *   <li>HR + OPS 合并去重含 user:manage + match:run</li>
 *   <li>未知角色返回空</li>
 *   <li>null 返回空</li>
 * </ul>
 */
class PermissionMappingTest {

    @Test
    void hrRole_containsMatchRun() {
        List<String> perms = PermissionMapping.getPermissions(List.of("HR"));
        assertNotNull(perms);
        assertFalse(perms.isEmpty(), "HR 应有权限");
        assertTrue(perms.contains("match:run"), "HR 应含 match:run");
        assertTrue(perms.contains("resume:analyze"), "HR 应含 resume:analyze");
    }

    @Test
    void hrAndOps_mergedAndDeduplicated() {
        List<String> perms = PermissionMapping.getPermissions(List.of("HR", "OPS"));
        assertNotNull(perms);
        // 合并后同时含 HR 独有 match:run 与 OPS 独有 user:manage
        assertTrue(perms.contains("match:run"), "合并后应含 HR 的 match:run");
        assertTrue(perms.contains("user:manage"), "合并后应含 OPS 的 user:manage");
        // 去重: resume:analyze 同时出现在 HR 与 OPS，合并后只出现一次
        long resumeCount = perms.stream().filter("resume:analyze"::equals).count();
        assertEquals(1, resumeCount, "重复权限应去重");
    }

    @Test
    void unknownRole_returnsEmpty() {
        List<String> perms = PermissionMapping.getPermissions(List.of("UNKNOWN"));
        assertNotNull(perms, "未知角色应返回非 null 空列表");
        assertTrue(perms.isEmpty(), "未知角色应无权限");
    }

    @Test
    void nullRoles_returnsEmpty() {
        List<String> perms = PermissionMapping.getPermissions(null);
        assertNotNull(perms, "null 入参应返回非 null 空列表");
        assertTrue(perms.isEmpty(), "null 入参应返回空");
    }
}
