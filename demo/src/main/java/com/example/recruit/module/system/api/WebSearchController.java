package com.example.recruit.module.system.api;

import com.example.recruit.agent.tool.WebSearchTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 联网搜索 HTTP 端点 (前端 /api/search)。
 *
 * <p>复用 WebSearchTool (Tavily) 的搜索能力, 暴露为 REST 接口供前端直接调用,
 * 返回 {@code {answer, sources, query[, mock]}} (由 ApiResponseAdvice 自动套统一信封)。
 * 未配置 Tavily API Key 或开启 mock 时, WebSearchTool 自行降级返回 Mock 结果。
 * 需登录 (SaTokenConfig 全局 /api/** 登录校验), 无角色限制。
 */
@RestController
@RequestMapping("/api/search")
public class WebSearchController {

    private final WebSearchTool webSearchTool;

    public WebSearchController(WebSearchTool webSearchTool) {
        this.webSearchTool = webSearchTool;
    }

    /** GET /api/search?q={query} —— 联网搜索。 */
    @GetMapping
    public Map<String, Object> search(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("搜索词不能为空");
        }
        return webSearchTool.doSearch(query);
    }
}
