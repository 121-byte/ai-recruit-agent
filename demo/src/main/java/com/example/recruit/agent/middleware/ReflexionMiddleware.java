package com.example.recruit.agent.middleware;

import com.example.recruit.llm.DeepSeekModelService;
import com.example.recruit.llm.JsonGuard;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Reflexion 反思中间件 (复刻自文档 §7.2 ReflexionMiddleware)。
 *
 * <p>实现 {@link MiddlewareBase#onActing} 在工具执行后评估输出质量，
 * 对关键工具 (matchCandidates / generateQuestions / generateOutreach) 的输出
 * 调用 LLM 按 相关性/完整性/合规性 三维打分，overall &lt; 0.75 时注入反思 Hint 触发重试。
 *
 * <p>重试计数使用 {@link ConcurrentHashMap} 按工具调用 ID 隔离，最多重试 {@link #MAX_RETRIES} 次。
 */
@Component
public class ReflexionMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ReflexionMiddleware.class);

    private static final int MAX_RETRIES = 2;
    private static final double SCORE_THRESHOLD = 0.75;

    /** 仅对这 3 个关键工具触发 LLM 质量评估。 */
    private static final Set<String> CRITICAL_TOOLS = Set.of(
            "matchCandidates", "generateQuestions", "generateOutreach");

    private static final String EVAL_PROMPT = """
            你是质量评估器。请对以下工具输出按维度打分：
            - 相关性 (0-1): 与任务需求的匹配度
            - 完整性 (0-1): 信息是否全面
            - 合规性 (0-1): 是否符合招聘规范
            请以JSON输出: {"relevance":0.8,"completeness":0.7,"compliance":0.9,"overall":0.8,"issues":"问题描述"}
            """;

    private final DeepSeekModelService deepSeekModelService;

    /** 按工具调用 ID 隔离的重试计数。 */
    private final ConcurrentHashMap<String, AtomicInteger> retryCount = new ConcurrentHashMap<>();

    public ReflexionMiddleware(DeepSeekModelService deepSeekModelService) {
        this.deepSeekModelService = deepSeekModelService;
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        Flux<AgentEvent> delegate = next.apply(input);

        // 非关键工具直接放行
        if (!hasCriticalTool(input)) {
            return delegate;
        }

        // 关键工具：收集结果文本，结束后评估，低质量则注入 Hint 触发重试
        StringBuilder acc = new StringBuilder();
        return delegate
                .doOnNext(e -> {
                    if (e instanceof ToolResultTextDeltaEvent d) {
                        acc.append(d.getDelta());
                    }
                })
                .collectList()
                .flatMapMany(events -> {
                    String text = acc.toString();
                    if (text.isBlank()) {
                        return Flux.fromIterable(events);
                    }
                    String toolCallId = firstCriticalToolCallId(input);
                    EvalResult eval = evaluate(text);
                    log.debug("Reflexion eval for {}: overall={}", toolCallId, eval.overall);

                    if (eval.overall >= SCORE_THRESHOLD) {
                        return Flux.fromIterable(events);
                    }
                    AtomicInteger counter = retryCount.computeIfAbsent(toolCallId, k -> new AtomicInteger(0));
                    if (counter.get() >= MAX_RETRIES) {
                        log.info("Reflexion: {} reached max retries ({}), accept result", toolCallId, MAX_RETRIES);
                        return Flux.fromIterable(events);
                    }
                    counter.incrementAndGet();
                    String hint = "上一条工具输出质量不足 (overall=" + eval.overall
                            + ")，问题：" + eval.issues + "。请调整参数重新调用。";
                    HintBlockEvent hintEvent = new HintBlockEvent(
                            "reflexion", "reflexion-block", "reflexion", hint);
                    return Flux.concat(Flux.just(hintEvent), Flux.fromIterable(events));
                });
    }

    private boolean hasCriticalTool(ActingInput input) {
        if (input == null) {
            return false;
        }
        List<ToolUseBlock> calls = input.toolCalls();
        if (calls == null) {
            return false;
        }
        for (ToolUseBlock call : calls) {
            if (call != null && CRITICAL_TOOLS.contains(call.getName())) {
                return true;
            }
        }
        return false;
    }

    private String firstCriticalToolCallId(ActingInput input) {
        if (input == null || input.toolCalls() == null) {
            return "unknown";
        }
        for (ToolUseBlock call : input.toolCalls()) {
            if (call != null && CRITICAL_TOOLS.contains(call.getName())) {
                return call.getId();
            }
        }
        return "unknown";
    }

    private EvalResult evaluate(String toolOutput) {
        try {
            String reply = deepSeekModelService.chatFast(EVAL_PROMPT, "工具输出:\n" + truncate(toolOutput, 500));
            JsonNode node = JsonGuard.parseJsonSafe(reply);
            if (node != null) {
                double overall = node.path("overall").asDouble(1.0);
                String issues = JsonGuard.text(node, "issues");
                return new EvalResult(overall, issues);
            }
        } catch (Exception e) {
            log.warn("Reflexion eval failed: {}", e.getMessage());
        }
        // 评估失败 → 放行 (不阻塞主流程)
        return new EvalResult(1.0, null);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private record EvalResult(double overall, String issues) {}
}
