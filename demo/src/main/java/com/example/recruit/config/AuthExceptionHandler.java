package com.example.recruit.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证异常处理器 (复刻自文档 §二 config/AuthExceptionHandler + §7.4 异常→HTTP 映射核对)。
 *
 * <p>统一将 Sa-Token 异常映射为标准 HTTP 状态码与 JSON 响应：
 * <ul>
 *   <li>NotLoginException → 401（未登录/登录过期）</li>
 *   <li>NotRoleException → 403（角色不足）</li>
 *   <li>NotPermissionException → 403（权限不足）</li>
 *   <li>其他 RuntimeException → 500</li>
 * </ul>
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("message", "未登录或登录已过期");
        body.put("detail", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Map<String, Object>> handleNotRole(NotRoleException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 403);
        body.put("message", "角色权限不足");
        body.put("role", e.getRole() == null ? null : e.getRole());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleNotPermission(NotPermissionException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 403);
        body.put("message", "权限不足");
        body.put("permission", e.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        log.error("unhandled runtime exception", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
