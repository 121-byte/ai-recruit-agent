package com.example.recruit.controller;

import com.example.recruit.dto.LoginRequest;
import com.example.recruit.dto.LoginResponse;
import com.example.recruit.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证 API (复刻自对齐清单 §5.10, @RequestMapping("/api/auth"))。
 *
 * <p>3 个权威端点:
 * <ul>
 *   <li>POST /login   用户登录 (返回 LoginResponse)</li>
 *   <li>POST /logout  用户登出</li>
 *   <li>GET  /me       获取当前用户信息 (返回 LoginResponse)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /login —— 用户登录, 返回 LoginResponse。 */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body) {
        String username = body.getUsername();
        String password = body.getPassword();
        Map<String, Object> result = authService.login(username, password);
        LoginResponse resp = toLoginResponse(result);
        return ResponseEntity.ok(resp);
    }

    /** POST /logout —— 用户登出。 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        authService.logout();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** GET /me —— 获取当前用户信息, 返回 LoginResponse。 */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me() {
        Map<String, Object> info = authService.currentUserInfo();
        LoginResponse resp = toLoginResponse(info);
        return ResponseEntity.ok(resp);
    }

    // ─────────────────── 工具 ───────────────────

    @SuppressWarnings("unchecked")
    private LoginResponse toLoginResponse(Map<String, Object> source) {
        LoginResponse resp = new LoginResponse();
        Object token = source.get("token");
        resp.setToken(token == null ? null : String.valueOf(token));

        Object userObj = source.get("user");
        Map<String, Object> userMap;
        if (userObj instanceof Map<?, ?> m) {
            userMap = (Map<String, Object>) m;
        } else {
            // me() 直接返回扁平的用户字段
            userMap = source;
        }
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        Object id = userMap.get("id");
        if (id != null) {
            try {
                info.setId(Long.parseLong(String.valueOf(id)));
            } catch (NumberFormatException ignored) {
            }
        }
        info.setUsername(strOrNull(userMap.get("username")));
        info.setRealName(strOrNull(userMap.get("realName")));
        info.setEmail(strOrNull(userMap.get("email")));
        info.setPhone(strOrNull(userMap.get("phone")));
        info.setDepartment(strOrNull(userMap.get("department")));
        Object roles = userMap.get("roles");
        if (roles instanceof List<?> list) {
            info.setRoles((List<String>) list);
        } else {
            info.setRoles(List.of());
        }
        resp.setUser(info);
        return resp;
    }

    private String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
