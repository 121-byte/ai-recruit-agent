package com.example.recruit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.recruit.dal.entity.SysRole;
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
 * 用户管理 API (复刻自实现文档 §3.3, @RequestMapping("/api/admin/users"))。
 *
 * <p>权威端点 (与 SaTokenConfig /api/admin/** OPS 规则 + 类级 @SaCheckRole("OPS") 双重鉴权):
 * <ul>
 *   <li>GET                用户列表 (含 roles, 不输出 password)</li>
 *   <li>GET  /roles        角色主数据 (供前端多选下拉)</li>
 *   <li>POST               创建用户 (BCrypt 加密, 默认 HR; 可传 roles 覆盖)</li>
 *   <li>PUT  /{id}         更新用户 (改密/启停; 空密码保留原值)</li>
 *   <li>DELETE /{id}        删除用户 (同步清理角色关联)</li>
 *   <li>PUT  /{id}/roles   分配角色 (先清后建)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/users")
@SaCheckRole("OPS")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /** GET —— 用户列表 (含 roles, 不输出 password)。 */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (SysUser u : userService.listAll()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", u.getId());
                row.put("username", u.getUsername());
                row.put("realName", u.getRealName());
                row.put("email", u.getEmail());
                row.put("phone", u.getPhone());
                row.put("department", u.getDepartment());
                row.put("status", u.getStatus());
                row.put("createdAt", u.getCreatedAt());
                row.put("roles", userService.roleCodesOf(u.getId()));   // 角色码数组
                result.add(row);                                        // 注意: 不 put password
            }
        } catch (Exception ignored) {
            // 返回已收集部分
        }
        return result;
    }

    /** GET /roles —— 角色主数据 (供前端角色多选下拉)。 */
    @GetMapping("/roles")
    public List<SysRole> roles() {
        return userService.listRoles();
    }

    /** POST —— 创建用户 (BCrypt 加密; 可传 roles 指定初始角色, 不传默认 HR)。 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setUsername(asString(body.get("username")));
        String password = asString(body.get("password"));
        user.setRealName(asString(body.get("realName")));
        user.setEmail(asString(body.get("email")));
        user.setPhone(asString(body.get("phone")));
        user.setDepartment(asString(body.get("department")));
        user.setStatus(asString(body.get("status")));
        SysUser created = userService.create(user, password);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (created == null) {
            resp.put("success", false);
            resp.put("message", "创建失败 (用户名可能已存在)");
            return ResponseEntity.badRequest().body(resp);
        }
        List<String> roleCodes = toStringList(body.get("roles"));
        if (!roleCodes.isEmpty()) {
            userService.assignRoles(created.getId(), roleCodes); // 覆盖默认 HR
        } else {
            roleCodes = userService.roleCodesOf(created.getId()); // 回显默认 HR
        }
        resp.put("success", true);
        resp.put("id", created.getId());
        resp.put("roles", roleCodes.isEmpty() ? List.of("HR") : roleCodes);
        return ResponseEntity.ok(resp);
    }

    /** PUT /{id} —— 更新用户 (含改密/启停; 空密码保留原值)。 */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody SysUser body) {
        body.setId(id);
        boolean ok = userService.update(body);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("updated", ok);
        return ResponseEntity.ok(resp);
    }

    /** DELETE /{id} —— 删除用户 (同步清理角色关联)。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean ok = userService.delete(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("deleted", ok);
        return ResponseEntity.ok(resp);
    }

    /** PUT /{id}/roles —— 分配角色 body {roles:["HR","OPS"]} (先清后建)。 */
    @PutMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> assignRoles(@PathVariable Long id,
                                                           @RequestBody Map<String, Object> body) {
        List<String> roleCodes = toStringList(body.get("roles"));
        userService.assignRoles(id, roleCodes);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("assigned", true);
        resp.put("roles", roleCodes);
        return ResponseEntity.ok(resp);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<String> toStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> codes = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    codes.add(String.valueOf(item));
                }
            }
            return codes;
        }
        return List.of();
    }
}
