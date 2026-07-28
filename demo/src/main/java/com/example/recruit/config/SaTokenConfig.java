package com.example.recruit.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由配置 (复刻自文档 §7.1 SaTokenConfig)。
 *
 * <p>路由级 RBAC：按 HTTP 方法+路径粒度校验角色（OPS 写 / HR 招聘 / SSE 自身订阅）。
 * 路径与 P2 Controller 对齐（如 /api/admin、/api/agent/sessions、/api/matches/job/{jobId}/run）。
 *
 * <p>Mock 模式 (app.mock.enabled=true) 放开登录校验便于无 Redis 验证；生产模式 (mock=false) 强制全部规则。
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
                return;  // Mock 模式: 无 Redis 时 Sa-Token 会话存储不可用, 放开
            }

            // ── 全局登录校验 (排除登录、健康检查) ──
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login")
                    .notMatch("/api/health/**")
                    .notMatch("/actuator/**")
                    .check(r -> StpUtil.checkLogin());

            // ── 用户管理 - 仅 OPS ──
            SaRouter.match("/api/admin/**", r -> StpUtil.checkRole("OPS"));

            // ── 岗位写操作 - 仅 OPS ──
            SaRouter.match("/api/jobs", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("OPS");
            });
            SaRouter.match("/api/jobs/*", r -> {
                String m = method();
                if ("PUT".equalsIgnoreCase(m) || "DELETE".equalsIgnoreCase(m)) StpUtil.checkRole("OPS");
            });

            // ── 简历删除 - 仅 OPS ──
            SaRouter.match("/api/resumes/*", r -> {
                if ("DELETE".equalsIgnoreCase(method())) StpUtil.checkRole("OPS");
            });

            // ── 候选人匹配: 执行/反馈 仅 HR ──
            SaRouter.match("/api/matches/job/*/run", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/matches/*/feedback", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });

            // ── 面试题生成/采纳 - 仅 HR ──
            SaRouter.match("/api/interviews/*/questions/generate", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/interviews/*/questions/*/adopt", r -> {
                if ("PUT".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });

            // ── AI 面试 - 仅 HR ──
            SaRouter.match("/api/interview-agent/interviews/*/start", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/interview-agent/interviews/*/assist", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/interview-agent/sessions/*/answer", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/interview-agent/sessions/*/answer/stream", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/interview-agent/sessions/*/end", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });

            // ── HITL 确认 - 仅 HR ──
            SaRouter.match("/api/agent/chat/confirm", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });

            // ── 离线评估执行与样本管理 - 仅 HR ──
            SaRouter.match("/api/evaluation/run*", r -> {
                if ("POST".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/evaluation/samples", r -> {
                String m = method();
                if ("POST".equalsIgnoreCase(m)) StpUtil.checkRole("HR");
            });
            SaRouter.match("/api/evaluation/samples/*", r -> {
                if ("DELETE".equalsIgnoreCase(method())) StpUtil.checkRole("HR");
            });

            // ── SSE 订阅 - 校验订阅自身 ──
            SaRouter.match("/api/events/subscribe/*", r -> {
                String pathUserId = lastPathSegment();
                Object loginId = StpUtil.getLoginIdDefaultNull();
                if (loginId == null || !pathUserId.equals(String.valueOf(loginId))) {
                    StpUtil.checkPermission("events:subscribe:all");
                }
            });
        })).addPathPatterns("/api/**");
    }

    private static String method() {
        try {
            return SaHolder.getRequest().getMethod();
        } catch (Throwable e) {
            return "";
        }
    }

    private static String lastPathSegment() {
        try {
            String path = SaHolder.getRequest().getRequestPath();
            int idx = path.lastIndexOf('/');
            return idx >= 0 ? path.substring(idx + 1) : path;
        } catch (Throwable e) {
            return "";
        }
    }
}
