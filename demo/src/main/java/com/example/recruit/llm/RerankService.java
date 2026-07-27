package com.example.recruit.llm;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Rerank 服务 (复刻自文档 §6.2，按阿里云百炼原生 rerank 端点对接)。
 *
 * <p>阿里云百炼 {@code qwen3-vl-rerank} 模型做交叉重排。
 * 注意：百炼 rerank 不在 OpenAI 兼容模式 (/rerank 返回 404)，而用原生端点
 * {@code /api/v1/services/rerank/text-rerank/text-rerank}，请求体为嵌套结构：
 * <pre>{"model":"qwen3-vl-rerank","input":{"query":..,"documents":[..]},"top_n":N}</pre>
 * 响应 {@code output.results[].index} 已按相关性降序，返回原始文档索引列表。
 *
 * <p>容错：API 调用失败时静默降级，返回原始顺序。
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 百炼原生 rerank 端点 (相对 baseUrl)。 */
    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final AppProperties props;
    private WebClient webClient;

    public RerankService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder()
                .baseUrl(props.getRerank().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (props.rerankKeyPresent() ? props.getRerank().getApiKey() : "mock"))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 对 documents 按 query 相关性重排，返回 topN 个文档的原始索引 (按相关性降序)。
     * 失败时返回原始顺序 (降级策略)。
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (useMock()) {
            return mockRerank(query, documents, topN);
        }
        try {
            ObjectNode requestBody = MAPPER.createObjectNode();
            requestBody.put("model", props.getRerank().getModel());
            ObjectNode input = requestBody.putObject("input");
            input.put("query", query == null ? "" : query);
            ArrayNode docs = input.putArray("documents");
            for (String d : documents) {
                docs.add(d == null ? "" : d);
            }
            requestBody.put("top_n", Math.min(topN, documents.size()));
            requestBody.put("return_documents", false);

            String response = webClient.post()
                    .uri(RERANK_PATH)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            // 响应: {"output":{"results":[{"index":0,"relevance_score":0.79}, ...]}, "usage":{...}}
            JsonNode results = MAPPER.readTree(response).path("output").path("results");
            List<Integer> indices = new ArrayList<>();
            results.forEach(r -> indices.add(r.path("index").asInt()));
            return indices.size() > topN ? indices.subList(0, topN) : indices;
        } catch (Exception e) {
            log.warn("Rerank failed, fallback to original order: {}", e.getMessage());
            return IntStream.range(0, documents.size()).boxed().toList();
        }
    }

    private boolean useMock() {
        return props.useMock() || !props.rerankKeyPresent();
    }

    /** Mock 重排：按与 query 的字符重叠度降序。 */
    private List<Integer> mockRerank(String query, List<String> documents, int topN) {
        List<String> queryChars = query == null ? List.of() :
                query.codePoints().mapToObj(String::valueOf).distinct().toList();
        return IntStream.range(0, documents.size())
                .boxed()
                .sorted((i, j) -> {
                    double si = overlap(queryChars, documents.get(i));
                    double sj = overlap(queryChars, documents.get(j));
                    return Double.compare(sj, si);
                })
                .limit(topN)
                .toList();
    }

    private double overlap(List<String> queryChars, String doc) {
        if (doc == null || queryChars.isEmpty()) {
            return 0;
        }
        long hit = queryChars.stream().filter(doc::contains).count();
        return (double) hit / queryChars.size();
    }
}
