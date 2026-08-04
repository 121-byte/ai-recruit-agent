package com.example.recruit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * 集中绑定 application.properties 中所有 {@code app.*} 配置项 (复刻自文档 §13.1)。
 *
 * <p>各服务从此取 key/baseUrl/model；当 key 为空且 {@code app.mock.enabled=true} 时，
 * 对应服务自动降级为 Mock 桩实现，保证无密钥也能启动。
 *
 * <p>注意: 用 {@code @Component} 而非 {@code @Configuration} —— 后者会被 CGLIB 代理,
 * 与 {@code @ConfigurationProperties} 同用会导致字段绑定失效 (字段停在默认值)。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Mock 降级总开关 (绑定自 app.mock.enabled, 嵌套对象以匹配点号路径) */
    private Mock mock = new Mock();

    private Ai ai = new Ai();
    private Embedding embedding = new Embedding();
    private Rerank rerank = new Rerank();
    private WebSearch webSearch = new WebSearch();
    private Langfuse langfuse = new Langfuse();
    private Fileparser fileparser = new Fileparser();
    private Intent intent = new Intent();

    /** app.mock.enabled 嵌套配置。 */
    @Data
    public static class Mock {
        private boolean enabled = true;
    }

    @Data
    public static class Ai {
        private String apiKey;
        private String baseUrl = "https://antchat.alipay.com/v1";
        private String modelPrimary = "openai:deepseek-v4-flash";
        private String modelFast = "openai:deepseek-v4-flash";
    }

    @Data
    public static class Embedding {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "text-embedding-v4";
        private int dimension = 1024;
    }

    @Data
    public static class Rerank {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen3-rerank";
    }

    @Data
    public static class WebSearch {
        private String apiKey;
        private String baseUrl = "https://api.tavily.com";
    }

    @Data
    public static class Langfuse {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:3000";
        private String publicKey;
        private String secretKey;
    }

    @Data
    public static class Fileparser {
        private String pythonCommand = "python3";
        private String scriptPath = "scripts/parse_resume.py";
    }

    /** app.intent.* 意图路由相关配置。 */
    @Data
    public static class Intent {
        private DynamicAnchor dynamicAnchor = new DynamicAnchor();

        /** 意图动态锚点自学习 (绑定 app.intent.dynamic-anchor.*)。默认关闭, 灰度开启。 */
        @Data
        public static class DynamicAnchor {
            private boolean enabled = false;
            /** 同桶语义去重阈值 (cosine ≥ 此值视为重复)。 */
            private double dedupThreshold = 0.90;
            /** 定时 flush 的 cron (默认每 5 分钟)。 */
            private String flushIntervalCron = "0 */5 * * * *";
            /** 每类意图动态锚点上限 (超出按最久未命中淘汰)。 */
            private int maxPerType = 200;
        }
    }

    /** 是否启用 Mock 降级：缺 key 或显式开启都视为 true。 */
    public boolean useMock() {
        return mock != null && mock.enabled;
    }

    public boolean aiKeyPresent() {
        return ai.apiKey != null && !ai.apiKey.isBlank();
    }

    public boolean embeddingKeyPresent() {
        return embedding.apiKey != null && !embedding.apiKey.isBlank();
    }

    public boolean rerankKeyPresent() {
        return rerank.apiKey != null && !rerank.apiKey.isBlank();
    }

    public boolean webSearchKeyPresent() {
        return webSearch.apiKey != null && !webSearch.apiKey.isBlank();
    }
}
