package com.example.recruit.llm;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * LangFuse 链路追踪服务 (复刻自文档 §9.2)。
 *
 * <p>可选的 LLM 调用链路追踪，通过 HTTP 发送到 LangFuse 自托管实例。
 * 默认关闭 ({@code app.langfuse.enabled=false})。
 */
@Service
public class LangFuseTraceService {

    private static final Logger log = LoggerFactory.getLogger(LangFuseTraceService.class);

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebClient webClient;

    public LangFuseTraceService(AppProperties props) {
        this.props = props;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        if (props.getLangfuse().isEnabled()) {
            this.webClient = WebClient.builder()
                    .baseUrl(props.getLangfuse().getBaseUrl())
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("LangFuse tracing enabled, base-url={}", props.getLangfuse().getBaseUrl());
        } else {
            log.info("LangFuse tracing disabled (app.langfuse.enabled=false)");
        }
    }

    public boolean isEnabled() {
        return props.getLangfuse().isEnabled() && webClient != null;
    }

    /**
     * 记录一次 LLM 调用到 LangFuse /api/public/ingestion 端点。
     * 失败静默，不影响主流程。
     */
    public void trace(String model, String prompt, String response, int tokens, long latencyMs) {
        traceGeneration(model, prompt, response, tokens, 0, latencyMs, null, null);
    }

    /**
     * 记录一次 LLM 调用 (generation) 到 LangFuse /api/public/ingestion，带 user/session 维度。
     * 失败静默，不影响主流程。
     */
    public void traceGeneration(String model, String prompt, String response,
                                int inputTokens, int outputTokens, long latencyMs,
                                String userId, String sessionId) {
        if (!isEnabled()) {
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("event", "generation");
            body.put("model", model);
            if (prompt != null) {
                body.put("prompt", prompt);
            }
            if (response != null) {
                body.put("response", response);
            }
            ObjectNode usage = mapper.createObjectNode();
            usage.put("input", inputTokens);
            usage.put("output", outputTokens);
            usage.put("total", inputTokens + outputTokens);
            body.set("usage", usage);
            body.put("latencyMs", latencyMs);
            if (userId != null) {
                body.put("userId", userId);
            }
            if (sessionId != null) {
                body.put("sessionId", sessionId);
            }
            postIngestion(body);
        } catch (Exception e) {
            log.debug("LangFuse traceGeneration skipped: {}", e.getMessage());
        }
    }

    /**
     * 记录一次失败的 LLM 调用 (error) 到 LangFuse /api/public/ingestion。
     * 失败静默，不影响主流程。
     */
    public void traceError(String model, String prompt, String error, long latencyMs) {
        if (!isEnabled()) {
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("event", "error");
            body.put("model", model);
            if (prompt != null) {
                body.put("prompt", prompt);
            }
            if (error != null) {
                body.put("error", error);
            }
            body.put("latencyMs", latencyMs);
            postIngestion(body);
        } catch (Exception e) {
            log.debug("LangFuse traceError skipped: {}", e.getMessage());
        }
    }

    private void postIngestion(ObjectNode body) {
        webClient.post()
                .uri("/api/public/ingestion")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .subscribe(
                        v -> {},
                        e -> log.debug("LangFuse ingestion failed: {}", e.getMessage())
                );
    }
}
