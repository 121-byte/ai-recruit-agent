package com.example.recruit.infra.llm;

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
import java.util.List;
import java.util.Map;

/**
 * 统一 LLM 调用入口 (复刻自文档 §9.1)。
 *
 * <p>封装 WebClient 调用 OpenAI 兼容接口 (端点由 app.ai.base-url 注入)。
 * 当未配置 API Key 或开启 Mock 模式时，返回桩数据，保证系统可启动可演示。
 *
 * <p>API 方法表 (文档 §9.1)：
 * <table>
 *   <tr><th>方法</th><th>模型</th><th>用途</th><th>返回</th></tr>
 *   <tr><td>chat(prompt)</td><td>primary</td><td>简单对话</td><td>String</td></tr>
 *   <tr><td>chat(sys,user)</td><td>primary</td><td>带系统提示</td><td>String</td></tr>
 *   <tr><td>chatFast(sys,user)</td><td>fast</td><td>快速推理</td><td>String</td></tr>
 *   <tr><td>chatFastWithUsage</td><td>fast</td><td>带 token 统计</td><td>ChatResult</td></tr>
 *   <tr><td>chatJson</td><td>primary</td><td>JSON 输出</td><td>String</td></tr>
 *   <tr><td>chatWithTools</td><td>primary</td><td>带工具调用</td><td>String</td></tr>
 *   <tr><td>chatStream</td><td>primary</td><td>流式</td><td>Flux&lt;String&gt;</td></tr>
 * </table>
 */
@Service
public class DeepSeekModelService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekModelService.class);

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private WebClient webClient;

    public DeepSeekModelService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder()
                .baseUrl(props.getAi().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (props.aiKeyPresent() ? props.getAi().getApiKey() : "mock"))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ─────────────────── 公共 API ───────────────────

    public String chat(String prompt) {
        return chat("You are a helpful assistant.", prompt);
    }

    public String chat(String systemPrompt, String userMessage) {
        return doChat(stripModelPrefix(props.getAi().getModelPrimary()), systemPrompt, userMessage);
    }

    public ChatResult chatWithUsage(String systemPrompt, String userMessage) {
        return doChatWithUsage(stripModelPrefix(props.getAi().getModelPrimary()), systemPrompt, userMessage);
    }

    public String chatFast(String systemPrompt, String userMessage) {
        return doChat(stripModelPrefix(props.getAi().getModelFast()), systemPrompt, userMessage);
    }

    public ChatResult chatFastWithUsage(String systemPrompt, String userMessage) {
        return doChatWithUsage(stripModelPrefix(props.getAi().getModelFast()), systemPrompt, userMessage);
    }

    public String chatJson(String systemPrompt, String userMessage) {
        String sys = systemPrompt + "\n请严格以 JSON 输出，不要包含 markdown 代码块标记。";
        return doChat(stripModelPrefix(props.getAi().getModelPrimary()), sys, userMessage);
    }

    public ChatResult chatJsonWithUsage(String systemPrompt, String userMessage) {
        String sys = systemPrompt + "\n请严格以 JSON 输出，不要包含 markdown 代码块标记。";
        return doChatWithUsage(stripModelPrefix(props.getAi().getModelPrimary()), sys, userMessage);
    }

    public String chatWithTools(String systemPrompt, String userMessage, List<Map<String, Object>> tools) {
        if (useMock()) {
            return mockToolReply(userMessage);
        }
        // 简化：实际工具调用由 AgentScope HarnessAgent 处理，此处保留入口
        return doChat(stripModelPrefix(props.getAi().getModelPrimary()), systemPrompt, userMessage);
    }

    public reactor.core.publisher.Flux<String> chatStream(String systemPrompt, String userMessage) {
        if (useMock()) {
            String reply = mockReply(userMessage);
            // 模拟逐字流式
            return reactor.core.publisher.Flux.create(sink -> {
                for (char c : reply.toCharArray()) {
                    sink.next(String.valueOf(c));
                }
                sink.complete();
            });
        }
        return doChatStream(stripModelPrefix(props.getAi().getModelPrimary()), systemPrompt, userMessage);
    }

    /**
     * 流式对话 (带 token usage)。OpenAI 兼容接口在 stream_options.include_usage=true 时,
     * 会在流的末尾追加一个携带 usage 的 chunk (delta 为空)。该方法在每个 chunk 上带出
     * delta 文本与 (仅末块) inputTokens/outputTokens, 供调用方按轮累计 token。
     */
    public reactor.core.publisher.Flux<StreamChunk> chatStreamWithUsage(String systemPrompt, String userMessage) {
        if (useMock()) {
            String reply = mockReply(userMessage);
            return reactor.core.publisher.Flux.create(sink -> {
                for (char c : reply.toCharArray()) {
                    sink.next(new StreamChunk(String.valueOf(c), "", 0, 0));
                }
                sink.complete();
            });
        }
        return doChatStreamWithUsage(stripModelPrefix(props.getAi().getModelPrimary()), systemPrompt, userMessage);
    }

    /**
     * 流式对话的单个 chunk: delta 为本轮正文增量, reasoning 为思维链增量 (DeepSeek 思考模式
     * 先流 reasoning_content 后流 content, 两者分离以便前端分别渲染到思考面板与正文气泡),
     * inputTokens/outputTokens 仅在含 usage 的末块非零。
     */
    public record StreamChunk(String delta, String reasoning, int inputTokens, int outputTokens) {
        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }


    // ─────────────────── 内部实现 ───────────────────

    private String doChat(String model, String systemPrompt, String userMessage) {
        if (useMock()) {
            return mockReply(userMessage);
        }
        try {
            String response = postChat(model, systemPrompt, userMessage, false);
            return extractContent(mapper.readTree(response));
        } catch (Exception e) {
            log.error("LLM chat failed: {}", e.getMessage());
            throw new IllegalStateException("LLM 调用失败，请稍后重试", e);
        }
    }

    private ChatResult doChatWithUsage(String model, String systemPrompt, String userMessage) {
        if (useMock()) {
            return new ChatResult(mockReply(userMessage), 0, 0);
        }
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String response = postChat(model, systemPrompt, userMessage, false);
                JsonNode root = mapper.readTree(response);
                String content = extractContent(root);
                int inputTokens = root.path("usage").path("prompt_tokens").asInt();
                int outputTokens = root.path("usage").path("completion_tokens").asInt();
                return new ChatResult(content, inputTokens, outputTokens);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                log.error("LLM chatWithUsage failed: {}", e.getMessage());
                throw new IllegalStateException("LLM 调用失败，请稍后重试", e);
            }
        }
        throw new IllegalStateException("LLM 调用超过重试次数");
    }

    /**
     * 提取 LLM 回复内容。deepseek-v4-flash 是推理模型，优先取 message.content，
     * 若为空 (推理未生成正文) 则回退到 message.reasoning_content。
     */
    private String extractContent(JsonNode root) {
        JsonNode msg = root.path("choices").path(0).path("message");
        String content = msg.path("content").asText("");
        if (content == null || content.isBlank()) {
            String reasoning = msg.path("reasoning_content").asText("");
            if (reasoning != null && !reasoning.isBlank()) {
                return "[reasoning] " + reasoning;
            }
        }
        return content == null ? "" : content;
    }

    private reactor.core.publisher.Flux<String> doChatStream(String model, String systemPrompt, String userMessage) {
        ObjectNode requestBody = buildRequestBody(model, systemPrompt, userMessage, true);
        requestBody.put("stream", true);
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(60))
                .filter(chunk -> !chunk.equals("[DONE]"))
                .map(chunk -> extractDeltaText(chunk))
                .filter(text -> !text.isEmpty())
                .onErrorResume(e -> {
                    log.warn("LLM stream error: {}", e.getMessage());
                    return reactor.core.publisher.Flux.error(new IllegalStateException("LLM 流式调用失败", e));
                });
    }

    /**
     * 带 usage 的流式: stream_options.include_usage=true, 末块携带 prompt_tokens/completion_tokens。
     */
    private reactor.core.publisher.Flux<StreamChunk> doChatStreamWithUsage(String model, String systemPrompt, String userMessage) {
        ObjectNode requestBody = buildRequestBody(model, systemPrompt, userMessage, true);
        requestBody.put("stream", true);
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(60))
                .filter(chunk -> !chunk.equals("[DONE]"))
                .map(chunk -> {
                    try {
                        String json = stripSsePrefix(chunk);
                        if (json == null || json.isEmpty() || "[DONE]".equals(json)) {
                            return new StreamChunk("", "", 0, 0);
                        }
                        JsonNode node = mapper.readTree(json);
                        JsonNode deltaNode = node.path("choices").path(0).path("delta");
                        String content = deltaNode.path("content").asText("");
                        String reasoning = deltaNode.path("reasoning_content").asText("");
                        JsonNode usage = node.path("usage");
                        int inTok = usage.isMissingNode() ? 0 : usage.path("prompt_tokens").asInt(0);
                        int outTok = usage.isMissingNode() ? 0 : usage.path("completion_tokens").asInt(0);
                        return new StreamChunk(content, reasoning, inTok, outTok);
                    } catch (Exception e) {
                        return new StreamChunk("", "", 0, 0);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("LLM stream(usage) error: {}", e.getMessage());
                    return reactor.core.publisher.Flux.error(new IllegalStateException("LLM 流式调用失败", e));
                });
    }

    /** 从一个 SSE 数据 chunk 中提取 delta 文本 (含 reasoning_content 回退)。
     * 兼容 "data: {...}\n" 格式（OpenAI SSE 流式）和裸 JSON 格式（非流式/Mock）。 */
    private String extractDeltaText(String chunk) {
        try {
            String json = stripSsePrefix(chunk);
            if (json == null || json.isEmpty() || "[DONE]".equals(json)) return "";
            JsonNode node = mapper.readTree(json);
            return extractDeltaFromNode(node);
        } catch (Exception e) {
            return "";
        }
    }

    /** 剥离 SSE "data: " 前缀，返回纯 JSON 字符串。 */
    private String stripSsePrefix(String chunk) {
        if (chunk == null) return null;
        String s = chunk.startsWith("data:") ? chunk.substring(5).trim() : chunk.trim();
        return s.isEmpty() ? null : s;
    }

    /** 从已解析的 JsonNode 中提取 delta 文本（content 优先，回退 reasoning_content）。 */
    private String extractDeltaFromNode(JsonNode node) {
        if (node == null) return "";
        JsonNode delta = node.path("choices").path(0).path("delta");
        String text = delta.path("content").asText("");
        if (text.isEmpty()) {
            text = delta.path("reasoning_content").asText("");
        }
        return text == null ? "" : text;
    }

    private String postChat(String model, String systemPrompt, String userMessage, boolean stream) {
        ObjectNode requestBody = buildRequestBody(model, systemPrompt, userMessage, stream);
        if (stream) {
            requestBody.put("stream", true);
        }
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();
    }

    private ObjectNode buildRequestBody(String model, String systemPrompt, String userMessage, boolean includeUsage) {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        if (includeUsage) {
            // OpenAI 兼容: 流式时在末块返回 usage (prompt_tokens/completion_tokens)
            ObjectNode streamOptions = mapper.createObjectNode();
            streamOptions.put("include_usage", true);
            requestBody.set("stream_options", streamOptions);
        }
        return requestBody;
    }

    /** 去掉 "openai:" 前缀 (文档 §9.1)。 */
    private String stripModelPrefix(String model) {
        if (model == null) {
            return "deepseek-v4-flash";
        }
        return model.startsWith("openai:") ? model.substring("openai:".length()) : model;
    }

    private boolean useMock() {
        return props.useMock() || !props.aiKeyPresent();
    }

    // ─────────────────── Mock 桩 ───────────────────

    private String mockReply(String userMessage) {
        if (userMessage == null) {
            return "[mock] 已收到空消息。";
        }
        String trimmed = userMessage.trim();
        if (trimmed.length() < 5) {
            return "[mock] 你说的「" + trimmed + "」我收到了，请详细描述你的招聘需求。";
        }
        return "[mock LLM 回复] 我理解你的需求是：「" + ellipsize(trimmed, 80)
                + "」。当前为 Mock 模式（未配置 API Key），配置 app.ai.api-key 后即可切换真实 DeepSeek 推理。"
                + " 我可以帮你完成岗位分析、候选人匹配、面试出题、AI 初面、候选人触达等全流程。";
    }

    private String mockToolReply(String userMessage) {
        return mockReply(userMessage);
    }

    private String ellipsize(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 调用方信息：遍历调用栈找到实际调用 doChat 的类名/方法名，用于日志关联 (文档 §9.1)。 */
    public String getCallerInfo() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length && i < 8; i++) {
            String cls = stack[i].getClassName();
            if (!cls.equals(this.getClass().getName()) && cls.startsWith("com.example.recruit")) {
                return cls + "#" + stack[i].getMethodName();
            }
        }
        return "unknown";
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
