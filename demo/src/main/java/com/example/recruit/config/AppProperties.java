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
    private Memory memory = new Memory();

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

    /**
     * app.memory.* 记忆生命周期配置 (绑定 app.memory.*)。
     *
     * <p>对齐 hebb-mind 艾宾浩斯留存曲线遗忘模型:
     * <pre>
     * eff_half_life = halfLifeDays * (1 + kImportance * importance + kAccess * min(accessCount,10)/10)
     * ttlExpiresAt  = lastAccess + eff_half_life * ln(1 / forgetThreshold)
     * 清扫: DELETE WHERE ttl_expires_at &lt; NOW() AND importance &lt; protectThreshold
     * 硬下限: ttlExpiresAt &gt;= createdAt + minRetentionDays
     * </pre>
     */
    @Data
    public static class Memory {
        /** 基础半衰期 (天): 一条中性记忆闲置这么多天后留存率衰减到约 37%。 */
        private double halfLifeDays = 60.0;
        /** 重要度对半衰期的拉长权重 (importance 0-1)。 */
        private double kImportance = 2.0;
        /** 访问次数对半衰期的拉长权重 (access_count 归一化为 min(n,10)/10)。 */
        private double kAccess = 1.5;
        /** 留存率低于此值即遗忘 (0-1)。 */
        private double forgetThreshold = 0.3;
        /** 任何记忆留存寿命的硬下限 (天), 防止瞬间删除。 */
        private int minRetentionDays = 1;
        /** 高重要度保护线: importance &gt;= 此值的记忆即使 TTL 到期也不被清扫删除。 */
        private double protectThreshold = 0.7;
        /** TTL 清扫 cron (注: @Scheduled 不读属性, 此值供 SchedulingConfigurer 后续动态化, 当前 cron 仍注解硬编码)。 */
        private String forgetCron = "0 30 3 * * *";
        /** 每 agent 活跃记忆容量上限 (超出按最低 importance LRU 删除)。 */
        private int maxPerUser = 200;

        /**
         * 计算有效半衰期 (天)。
         *
         * @param importance 0-1, null 视为 0.5
         * @param accessCount 访问次数, null 视为 0
         */
        public double effectiveHalfLife(Double importance, Integer accessCount) {
            double imp = importance == null ? 0.5 : importance;
            int acc = accessCount == null ? 0 : Math.min(accessCount, 10);
            return halfLifeDays * (1.0 + kImportance * imp + kAccess * (acc / 10.0));
        }

        /**
         * 计算给定 importance/accessCount 下的 TTL 过期点 (从基准时间起)。
         * ttl = base + eff_half_life * ln(1/forgetThreshold), 不早于 base + minRetentionDays。
         */
        public java.time.LocalDateTime computeTtl(java.time.LocalDateTime base,
                                                  Double importance, Integer accessCount) {
            double eff = effectiveHalfLife(importance, accessCount);
            long ttlSeconds = (long) (eff * Math.log(1.0 / forgetThreshold) * 24 * 3600);
            long minSeconds = (long) minRetentionDays * 24 * 3600L;
            long seconds = Math.max(ttlSeconds, minSeconds);
            return base.plusSeconds(seconds);
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
