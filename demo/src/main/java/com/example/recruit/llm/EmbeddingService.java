package com.example.recruit.llm;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Embedding 服务 (复刻自文档 §6.1)。
 *
 * <p>调用阿里云百炼 {@code text-embedding-v4} 模型，生成 1024 维向量。
 * 用于简历、岗位、记忆、意图锚点的向量化。
 *
 * <p>WebClient 配置：baseUrl={@code https://dashscope.aliyuncs.com/compatible-mode/v1}，
 * Header {@code Authorization: Bearer {api-key}}，超时 10 秒。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebClient webClient;

    public EmbeddingService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder()
                .baseUrl(props.getEmbedding().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (props.embeddingKeyPresent() ? props.getEmbedding().getApiKey() : "mock"))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 生成文本的 1024 维向量。
     *
     * <p>Mock 模式下返回基于文本哈希的确定性伪向量，保证余弦相似度计算可演示
     * (相同文本 → 相同向量，相似文本 → 局部重叠)。
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[props.getEmbedding().getDimension()];
        }
        if (useMock()) {
            return mockEmbed(text);
        }
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", props.getEmbedding().getModel());
            requestBody.put("input", text);
            requestBody.put("dimension", props.getEmbedding().getDimension());
            requestBody.put("encoding_format", "float");

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode embeddingNode = mapper.readTree(response)
                    .path("data").get(0).path("embedding");
            int dim = props.getEmbedding().getDimension();
            float[] result = new float[dim];
            for (int i = 0; i < dim && i < embeddingNode.size(); i++) {
                result[i] = (float) embeddingNode.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            log.error("Embedding failed, fallback to mock: {}", e.getMessage());
            return mockEmbed(text);
        }
    }

    // ─────────────────── Mock ───────────────────

    private boolean useMock() {
        return props.useMock() || !props.embeddingKeyPresent();
    }

    /**
     * 确定性伪向量：以文本字符为种子填满 1024 维，相同文本→相同向量，
     * 字符重叠的文本→向量方向相近，使余弦相似度具备可演示性。
     */
    private float[] mockEmbed(String text) {
        int dim = props.getEmbedding().getDimension();
        float[] v = new float[dim];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        // 用文本字节循环填充，再归一化
        double norm = 0;
        for (int i = 0; i < dim; i++) {
            int b = bytes[i % bytes.length] & 0xFF;
            int idx = (i * 31 + b) % 256;
            v[i] = (idx - 128) / 128.0f;
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }

    public int dimension() {
        return props.getEmbedding().getDimension();
    }
}
