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
        if (!isEnabled()) {
            return;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("response", response);
            body.put("tokens", tokens);
            body.put("latencyMs", latencyMs);
            webClient.post()
                    .uri("/api/public/ingestion")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            v -> {},
                            e -> log.debug("LangFuse trace failed: {}", e.getMessage())
                    );
        } catch (Exception e) {
            log.debug("LangFuse trace skipped: {}", e.getMessage());
        }
    }
}
