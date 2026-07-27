package com.example.recruit.controller;

import com.example.recruit.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证 API (复刻自文档 §14.1)。
 *
 * <p>POST /api/auth/login  用户登录
 * <p>POST /api/auth/logout 用户登出
 * <p>GET  /api/auth/userinfo 获取当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = body.get("username") == null ? null : String.valueOf(body.get("username"));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        return authService.login(username, password);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        authService.logout();
        return Map.of("status", "ok");
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userinfo() {
        return authService.currentUserInfo();
    }
}
