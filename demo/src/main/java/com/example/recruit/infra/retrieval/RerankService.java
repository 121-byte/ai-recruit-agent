package com.example.recruit.infra.retrieval;

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
 * Rerank service for recruitment matching.
 *
 * <p>The legacy {@link #rerank(String, List, int)} method returns document indices only.
 * Candidate matching uses {@link #rerankWithScore(String, List, int)} so the rerank signal
 * can be fused into the final score.
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";
    private static final String INSTRUCT = "根据岗位需求，按技术技能匹配度和相关工作经验对候选人简历排序";
    private static final int MAX_DOC_CHARS = 800;

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

    public List<Integer> rerank(String query, List<String> documents, int topN) {
        return rerankWithScore(query, documents, topN).stream()
                .map(RerankResult::index)
                .toList();
    }

    public List<RerankResult> rerankWithScore(String query, List<String> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (documents.size() == 1 || topN <= 0) {
            return originalOrder(documents.size(), Math.max(topN, 1));
        }
        if (useMock()) {
            return mockRerank(query, documents, topN);
        }
        try {
            ObjectNode requestBody = MAPPER.createObjectNode();
            requestBody.put("model", props.getRerank().getModel());

            ObjectNode input = requestBody.putObject("input");
            input.put("query", query == null ? "" : query);
            input.put("instruct", INSTRUCT);

            ArrayNode docs = input.putArray("documents");
            for (String document : documents) {
                String truncated = document == null
                        ? ""
                        : document.substring(0, Math.min(document.length(), MAX_DOC_CHARS));
                docs.add(truncated);
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

            JsonNode results = MAPPER.readTree(response).path("output").path("results");
            List<RerankResult> ranked = new ArrayList<>();
            results.forEach(result -> ranked.add(new RerankResult(
                    result.path("index").asInt(),
                    normalizeScore(result.path("relevance_score").asDouble(0.0)))));
            if (ranked.isEmpty()) {
                return originalOrder(documents.size(), topN);
            }
            return ranked.size() > topN ? ranked.subList(0, topN) : ranked;
        } catch (Exception e) {
            log.warn("Rerank failed, using vector order: {}", e.getMessage());
            return originalOrder(documents.size(), topN);
        }
    }

    private boolean useMock() {
        return props.useMock() || !props.rerankKeyPresent();
    }

    private List<RerankResult> mockRerank(String query, List<String> documents, int topN) {
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
                .map(i -> new RerankResult(i, normalizeScore(overlap(queryChars, documents.get(i)))))
                .toList();
    }

    private double overlap(List<String> queryChars, String doc) {
        if (doc == null || queryChars.isEmpty()) {
            return 0;
        }
        long hit = queryChars.stream().filter(doc::contains).count();
        return (double) hit / queryChars.size();
    }

    private List<RerankResult> originalOrder(int size, int topN) {
        int limit = Math.min(size, Math.max(topN, 0));
        return IntStream.range(0, limit)
                .mapToObj(i -> new RerankResult(i, rankScore(i)))
                .toList();
    }

    private double rankScore(int zeroBasedRank) {
        return Math.max(0.0, 100.0 - zeroBasedRank * 5.0);
    }

    private double normalizeScore(double raw) {
        if (raw <= 1.0) {
            return Math.max(0.0, Math.min(100.0, raw * 100.0));
        }
        return Math.max(0.0, Math.min(100.0, raw));
    }

    public record RerankResult(int index, double score) {}
}
