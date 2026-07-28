package com.example.recruit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 API (复刻自对齐清单 §5.8, @RequestMapping("/api/admin/users"))。
 *
 * <p>5 个权威端点 (与 SaTokenConfig /api/admin/** OPS 规则配对):
 * <ul>
 *   <li>GET                用户列表 (password 置空)</li>
 *   <li>POST               创建用户 (BCrypt 加密, 默认关联 HR 角色)</li>
 *   <li>PUT  /{id}         更新用户</li>
 *   <li>DELETE /{id}        删除用户</li>
 *   <li>PUT  /{id}/roles   分配角色</li>
 * </ul>
 *
 * <p>权限: P3 将 /api/admin/** 配置为 OPS; 本阶段先以 @SaCheckRole("OPS") 占位。
 */
@RestController
@RequestMapping("/api/admin/users")
@SaCheckRole("OPS")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /** GET —— 用户列表 (password 置空)。 */
    @GetMapping
    public List<SysUser> list() {
        try {
            List<SysUser> users = userService.listAll();
            for (SysUser u : users) {
                u.setPassword(null);
            }
            return users;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** POST —— 创建用户 (rawPassword 明文, BCrypt 加密)。 */
    @PostMapping
    public ResponseEntity<SysUser> create(@RequestBody Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setUsername(body.get("username") == null ? null : String.valueOf(body.get("username")));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        user.setRealName(body.get("realName") == null ? null : String.valueOf(body.get("realName")));
        user.setEmail(body.get("email") == null ? null : String.valueOf(body.get("email")));
        user.setPhone(body.get("phone") == null ? null : String.valueOf(body.get("phone")));
        user.setDepartment(body.get("department") == null ? null : String.valueOf(body.get("department")));
        try {
            userService.create(user, password);
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(user);
    }

    /** PUT /{id} —— 更新用户。 */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                       @RequestBody SysUser body) {
        body.setId(id);
        boolean ok = userService.update(body);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("updated", ok);
        return ResponseEntity.ok(resp);
    }

    /** DELETE /{id} —— 删除用户。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean ok = userService.delete(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("deleted", ok);
        return ResponseEntity.ok(resp);
    }

    /** PUT /{id}/roles —— 分配角色 (body {roles:["HR","OPS"]})。 */
    @PutMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> assignRoles(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> roleCodes = body.get("roles") == null ? List.of() : (List<String>) body.get("roles");
        boolean ok = userService.assignRoles(id, roleCodes);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("assigned", ok);
        resp.put("roles", roleCodes);
        return ResponseEntity.ok(resp);
    }
}
