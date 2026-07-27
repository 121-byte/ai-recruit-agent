package com.example.recruit.config;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证异常处理器 (复刻自文档 §二 config/AuthExceptionHandler)。
 *
 * <p>统一将 Sa-Token 未登录、业务异常转为 JSON 响应。
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("message", "未登录或登录已过期");
        body.put("detail", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
