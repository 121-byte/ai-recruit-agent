package com.example.recruit.agent.tool;

import com.example.recruit.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索工具 (复刻自文档 §8.8 WebSearchTool)。
 *
 * <p>调用 Tavily API 做联网搜索，补充实时信息（行业薪资、技术趋势）。
 * 引用时用 [1] [2] 标注来源。Mock 模式返回桩结果。
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppProperties props;
    private WebClient webClient;

    public WebSearchTool(AppProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.getWebSearch().getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Tool(
            name = "webSearch",
            description = "联网搜索实时信息：行业薪资、技术趋势、公司背景等。返回带 [1][2] 来源标注的结果列表。",
            readOnly = true,
            concurrencySafe = true)
    public Map<String, Object> webSearch(
            @ToolParam(name = "query", description = "搜索查询词，如 '2026 Java 后端薪资水平'")
            String query) {

        if (useMock()) {
            return mockSearch(query);
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("query", query);
            body.put("max_results", 5);
            body.put("search_depth", "basic");

            String response = webClient.post()
                    .uri("/search")
                    .header("Authorization", "Bearer " + props.getWebSearch().getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = MAPPER.readTree(response);
            JsonNode results = root.path("results");
            List<Map<String, Object>> sources = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (JsonNode r : results) {
                String title = r.path("title").asText();
                String url = r.path("url").asText();
                String content = r.path("content").asText();
                sb.append("[").append(idx).append("] ").append(title).append('\n');
                sb.append(content).append('\n');
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("index", idx);
                src.put("title", title);
                src.put("url", url);
                sources.add(src);
                idx++;
            }
            return Map.of("answer", sb.toString(), "sources", sources, "query", query);
        } catch (Exception e) {
            log.warn("webSearch failed: {}", e.getMessage());
            throw new IllegalStateException("联网搜索失败，请稍后重试", e);
        }
    }

    private boolean useMock() {
        return props.useMock() || !props.webSearchKeyPresent();
    }

    private Map<String, Object> mockSearch(String query) {
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("index", 1);
        s1.put("title", "[Mock] 搜索结果 - " + query);
        s1.put("url", "https://example.com/result-1");
        sources.add(s1);
        String answer = "[1] [Mock 搜索] 未配置 Tavily API Key，无法联网搜索「" + query
                + "」。配置 app.web-search.api-key 后可获取真实结果。";
        return Map.of("answer", answer, "sources", sources, "query", query, "mock", true);
    }
}
