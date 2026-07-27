package com.example.recruit.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.Msg;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 输入护栏中间件 (复刻自文档 §7.1 ConversationGuardrail)。
 *
 * <p>实现 {@link MiddlewareBase#onAgent} 在 Agent 处理前拦截恶意输入：
 * <ol>
 *   <li>Prompt Injection 检测：忽略/覆盖系统提示、角色劫持、泄露系统提示等</li>
 *   <li>招聘偏见检测：性别/民族/户籍/学历歧视等</li>
 * </ol>
 *
 * <p>拦截时返回 {@code guardrail_blocked} 自定义事件，不进入 Agent 执行。
 */
@Component
public class ConversationGuardrail implements MiddlewareBase {

    // ─── Prompt Injection 检测 (文档 §7.1) ───
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?i)(ignore|disregard|forget).*(previous|above|system).*(instruction|prompt|rule)|" +
            "(?i)you\\s+are\\s+(now|no\\s+longer)|" +
            "(?i)(reveal|show|print).*(system|hidden|secret).*(prompt|instruction)|" +
            "(?i)act\\s+as\\s+(if\\s+you\\s+have\\s+no|without).*(rule|restriction|guardrail)");

    // ─── 招聘偏见检测 (文档 §7.1) ───
    private static final Pattern BIAS_INPUT = Pattern.compile(
            "(?i)(只|仅)(招|聘|要|推荐)(男|女|汉族|少数民族|本地|外地)|" +
            "(?i)(不要|拒|排除)(女|男|大龄|残障|已婚|未婚|怀孕)|" +
            "(?i)(只要|仅限)(985|211|全日制|统招)");

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                     java.util.function.Function<AgentInput, Flux<AgentEvent>> next) {
        String userText = extractUserText(input);
        if (userText != null) {
            GuardResult result = checkInput(userText);
            if (result.blocked()) {
                Map<String, Object> data = Map.of(
                        "error", "消息被安全护栏拦截: " + result.reason(),
                        "reason", result.reason());
                return Flux.just(new CustomEvent("guardrail_blocked", data));
            }
        }
        return next.apply(input);
    }

    /** 从 AgentInput 提取最近一条用户消息文本。 */
    private String extractUserText(AgentInput input) {
        if (input == null) {
            return null;
        }
        List<Msg> msgs = input.msgs();
        if (msgs == null || msgs.isEmpty()) {
            return null;
        }
        // 从后往前找最近一条 user 消息
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg m = msgs.get(i);
            try {
                String role = m.getRole() == null ? "" : m.getRole().name();
                if (role.equalsIgnoreCase("USER")) {
                    return m.getTextContent();
                }
            } catch (Throwable ignored) {
                return m.getTextContent();
            }
        }
        return msgs.get(msgs.size() - 1).getTextContent();
    }

    /**
     * 输入安全检查。先匹配 Injection，再匹配偏见。
     */
    public GuardResult checkInput(String text) {
        if (text == null || text.isBlank()) {
            return new GuardResult(false, null);
        }
        if (PROMPT_INJECTION.matcher(text).find()) {
            return new GuardResult(true, "检测到提示词注入尝试");
        }
        if (BIAS_INPUT.matcher(text).find()) {
            return new GuardResult(true, "检测到招聘歧视性表述");
        }
        return new GuardResult(false, null);
    }

    /** 护栏检查结果。 */
    public record GuardResult(boolean blocked, String reason) {}
}
