package com.example.recruit.module.system.api;

import com.example.recruit.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查 API (复刻自文档 §14.14)。
 *
 * <p>GET /api/health 健康检查
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AppProperties appProperties;

    public HealthController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "UP");
        resp.put("mock", appProperties.useMock());
        return resp;
    }
}
