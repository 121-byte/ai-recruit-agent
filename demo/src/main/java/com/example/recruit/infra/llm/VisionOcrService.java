package com.example.recruit.infra.llm;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * 视觉 OCR 服务: 调阿里云百炼 qwen-image 视觉模型识别简历图片文字。
 *
 * <p>用于 {@link com.example.recruit.infra.fileparser.FileParserUtil} 的扫描件 PDF 兜底:
 * 当 PDFBox 提取文本过短(疑似扫描件)时, 渲染 PDF 页为图片, 调本服务 OCR 提取文本。
 *
 * <p>调用百炼 OpenAI 兼容 multimodal 接口: {@code POST {baseUrl}/chat/completions},
 * body {@code messages[].content} 为多模态数组 (text + image_url[data URL])。
 * 响应 {@code choices[0].message.content} 即识别文本。
 *
 * <p>未配置 key 或全局 mock 时 {@link #available()} 返回 false, 调用方降级 markitdown。
 */
@Component
public class VisionOcrService {

    private static final Logger log = LoggerFactory.getLogger(VisionOcrService.class);

    private static final String OCR_SYSTEM =
            "你是简历 OCR 助手。请识别图片中的全部文字, 按从上到下、从左到右的原始排版输出纯文本。"
                    + "多张图片按顺序拼接。不要解释、不要添加 markdown 或任何格式标记, 只输出识别到的文字。";

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebClient webClient;

    public VisionOcrService(AppProperties props) {
        this.props = props;
    }

    // @PostConstruct 也能用, 但与 DeepSeekModelService 一致风格; 此处 init 懒加载避免无 key 时报错
    private WebClient client() {
        if (webClient == null) {
            webClient = WebClient.builder()
                    .baseUrl(props.getVision().getBaseUrl())
                    .defaultHeader("Authorization",
                            "Bearer " + (props.visionKeyPresent() ? props.getVision().getApiKey() : "mock"))
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }
        return webClient;
    }

    /** 视觉 OCR 是否可用: 已配 key 且非全局 mock。 */
    public boolean available() {
        return props.visionKeyPresent() && !props.useMock();
    }

    /**
     * 对多张图片(data URL 形式 {@code data:image/png;base64,...})做 OCR, 返回拼接文本。
     * 调用失败返回 null, 由调用方降级。
     */
    public String ocrImages(List<String> imageDataUrls) {
        if (imageDataUrls == null || imageDataUrls.isEmpty()) {
            return null;
        }
        if (!available()) {
            log.debug("vision OCR unavailable (no key or mock), skip");
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", props.getVision().getModel());
            ArrayNode content = mapper.createArrayNode();
            content.add(mapper.createObjectNode()
                    .put("type", "text")
                    .put("text", OCR_SYSTEM));
            for (String url : imageDataUrls) {
                content.add(mapper.createObjectNode()
                        .put("type", "image_url")
                        .putPOJO("image_url", java.util.Map.of("url", url)));
            }
            ArrayNode messages = mapper.createArrayNode();
            messages.add(mapper.createObjectNode()
                    .put("role", "user")
                    .putPOJO("content", content));
            body.putPOJO("messages", messages);

            String resp = client().post()
                    .uri("/chat/completions")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            JsonNode root = mapper.readTree(resp);
            JsonNode msg = root.path("choices").path(0).path("message");
            // qwen3.5-ocr 优先取 ocr_result.processed_text (干净 OCR 文本), 回退 content
            JsonNode ocrResult = msg.path("ocr_result").path("processed_text");
            if (!ocrResult.isMissingNode() && !ocrResult.asText("").isBlank()) {
                return ocrResult.asText();
            }
            JsonNode msgContent = msg.path("content");
            if (msgContent.isMissingNode() || msgContent.asText("").isBlank()) {
                log.warn("vision OCR empty response: {}", truncate(resp, 300));
                return null;
            }
            return msgContent.asText();
        } catch (Exception e) {
            log.warn("vision OCR failed: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max) + "...");
    }
}
