package com.example.recruit.config;

import org.springframework.context.annotation.Configuration;

/**
 * 安全配置 (复刻自文档 §7.4 SecurityConfig)。
 *
 * <p>认证授权由 Sa-Token 负责：路由级拦截器见 {@link SaTokenConfig}（全局登录 + OPS/HR 角色规则），
 * 角色-权限映射见 {@link com.example.recruit.security.PermissionMapping}，
 * HITL 检查点见 {@link com.example.recruit.agent.routing.RecruitmentPermissionService}。
 *
 * <p>本项目不引入完整 Spring Security（仅用 spring-security-crypto 做 BCrypt），
 * 本类作为安全链扩展点保留，当前不注册额外过滤器。
 */
@Configuration
public class SecurityConfig {
    // Sa-Token 拦截器 (SaTokenConfig) 已覆盖鉴权; 此处为扩展点。
}
