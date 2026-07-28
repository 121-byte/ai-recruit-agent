package com.example.recruit.dto;

/**
 * 登录请求 DTO (复刻对齐清单 §第四部分 dto/LoginRequest)。
 * 用于 AuthController 登录接口。
 */
public class LoginRequest {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
