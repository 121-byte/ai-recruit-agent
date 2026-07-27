package com.example.recruit.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由配置 (复刻自文档 §7.4 SaTokenConfig)。
 *
 * <p>对 /api/** 启用登录校验，放行登录、健康检查、Actuator。
 *
 * <p>Mock 模式 (app.mock.enabled=true) 下放开登录校验，便于无 Redis 环境验证 SSE 流；
 * 生产模式 (mock=false) 照常鉴权。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public SaTokenConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        boolean mock = appProperties.useMock();
        registry.addInterceptor(new SaInterceptor(handle -> {
            if (mock) {
                // Mock 模式: 无 Redis 时 Sa-Token 会话存储不可用, 放开校验以便验证
                return;
            }
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login", "/api/health", "/actuator/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}

