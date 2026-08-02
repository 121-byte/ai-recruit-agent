package com.example.recruit.agent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AllToolsDeniedEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.DataBlockDeltaEvent;
import io.agentscope.core.event.DataBlockEndEvent;
import io.agentscope.core.event.DataBlockStartEvent;
import io.agentscope.core.event.ExternalExecutionResultEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgentEvent → SSE 字符串映射 + PII 脱敏 (复刻自文档 §10.2 + §7.5)。
 *
 * <p>将 AgentScope 的 28 种 {@link AgentEvent} 子类映射为 14 种 SSE 事件类型。
 * 在 SSE 输出层对 PII (手机号/身份证/邮箱) 脱敏——因为 AgentScope 的 Msg 不可变，
 * 中间件无法改输出，SSE 映射层是输出到用户的最后一站。
 *
 * <p>SSE 事件类型 (文档 §10.2)：
 * session / plan / task_update / thinking / text / tool_call / tool_result /
 * hint / data / hitl / push / trace / stop / error / done / stats
 *
 * <p>注意真实事件类名与文档的偏差 (已用 javap 核实)：
 * <ul>
 *   <li>文档 TextDeltaEvent → 真实 {@link TextBlockDeltaEvent}，取值用 getDelta()</li>
 *   <li>文档 ToolCallEvent → 真实 {@link ToolCallStartEvent}，getName→getToolCallName</li>
 *   <li>文档 ToolResultEvent.getResult() → 结果文本走 {@link ToolResultTextDeltaEvent} 流式
 *       + {@link ToolResultEndEvent} 标记结束 (无单一 result 字段)</li>
 * </ul>
 */
@Component
public class AgentEventSseMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 工具进度提示映射 (文档 §10.2 TOOL_PROGRESS_HINTS)。 */
    private static final Map<String, String> TOOL_PROGRESS_HINTS = Map.ofEntries(
            Map.entry("listJobs", "正在查询岗位列表..."),
            Map.entry("analyzeJob", "正在分析岗位详情..."),
            Map.entry("matchCandidates", "正在分析候选人匹配..."),
            Map.entry("generateQuestions", "正在生成面试题..."),
            Map.entry("webSearch", "正在联网搜索..."),
            Map.entry("generateOutreach", "正在生成邀约草稿..."),
            Map.entry("startInterview", "正在启动AI面试..."),
            Map.entry("searchResumes", "正在搜索简历..."),
            Map.entry("jobAnalyst", "正在调用岗位分析专家..."),
            Map.entry("matchAgent", "正在调用候选人匹配专家..."),
            Map.entry("interviewSpecialist", "正在调用面试专家..."),
            Map.entry("outreachSpecialist", "正在调用触达专家...")
    );

    /**
     * 将 AgentScope AgentEvent 转换为 SSE 帧字符串。
     *
     * @return SSE 帧字符串，或 null 表示静默跳过该事件
     */
    public String toSse(AgentEvent event) {
        if (event == null) {
            return null;
        }
        AgentEventType type = event.getType();

        // ─── 文本块 (text) ───
        if (event instanceof TextBlockStartEvent) {
            return format("text", mapOf("delta", "", "isLast", false));
        }
        if (event instanceof TextBlockDeltaEvent e) {
            return format("text", mapOf("delta", maskPii(e.getDelta()), "isLast", false));
        }
        if (event instanceof TextBlockEndEvent) {
            return format("text", mapOf("delta", "", "isLast", true));
        }

        // ─── 思考块 (thinking) ───
        if (event instanceof ThinkingBlockStartEvent) {
            return format("thinking", mapOf("active", true));
        }
        if (event instanceof ThinkingBlockDeltaEvent e) {
            return format("thinking", mapOf("delta", e.getDelta(), "isLast", false));
        }
        if (event instanceof ThinkingBlockEndEvent) {
            return format("thinking", mapOf("delta", "", "active", false, "isLast", true));
        }

        // ─── 工具调用 (tool_call) ───
        if (event instanceof ToolCallStartEvent e) {
            String hint = getProgressHint(e.getToolCallName());
            return format("tool_call", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "progressHint", hint));
        }
        if (event instanceof ToolCallDeltaEvent e) {
            return format("tool_call", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "delta", e.getDelta()));
        }
        if (event instanceof ToolCallEndEvent e) {
            return format("tool_call", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "finished", true));
        }

        // ─── 工具结果 (tool_result) — 流式文本 + 数据 delta 均经 maskPii ───
        if (event instanceof ToolResultStartEvent e) {
            return format("tool_result", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "started", true));
        }
        if (event instanceof ToolResultTextDeltaEvent e) {
            return format("tool_result", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "result", maskPii(e.getDelta())));
        }
        if (event instanceof ToolResultDataDeltaEvent e) {
            Object data = e.getData();
            return format("tool_result", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "result", data == null ? null : maskPii(String.valueOf(data))));
        }
        if (event instanceof ToolResultEndEvent e) {
            return format("tool_result", mapOf(
                    "name", e.getToolCallName(),
                    "toolCallId", e.getToolCallId(),
                    "state", e.getState() == null ? null : e.getState().name(),
                    "finished", true));
        }

        // ─── 数据块 (data) ───
        if (event instanceof DataBlockStartEvent) {
            return format("data", mapOf("active", true));
        }
        if (event instanceof DataBlockDeltaEvent e) {
            return format("data", mapOf("delta", e.getDelta()));
        }
        if (event instanceof DataBlockEndEvent) {
            return format("data", mapOf("delta", "", "isLast", true));
        }

        // ─── 模型调用 (trace: model/tokens/latency) ───
        if (event instanceof ModelCallStartEvent) {
            return format("trace", mapOf("phase", "start"));
        }
        if (event instanceof ModelCallEndEvent e) {
            ChatUsage u = e.getUsage();
            return format("trace", mapOf(
                    "phase", "end",
                    "inputTokens", u == null ? null : u.getInputTokens(),
                    "outputTokens", u == null ? null : u.getOutputTokens(),
                    "totalTokens", u == null ? null : u.getTotalTokens(),
                    "latencySec", u == null ? null : u.getTime()));
        }

        // ─── Reflexion 反思提示 (hint) ───
        if (event instanceof HintBlockEvent e) {
            return format("hint", mapOf("hint", e.getHint()));
        }

        // ─── Agent 结果 (done, result 文本经 maskPii) ───
        if (event instanceof AgentResultEvent e) {
            Msg result = e.getResult();
            String text = result == null ? null : maskPii(result.getTextContent());
            return format("done", mapOf("result", text));
        }

        // ─── 自定义事件 (护栏拦截等) ───
        if (event instanceof CustomEvent e) {
            if ("guardrail_blocked".equals(e.getName())) {
                return format("error", e.getValue());
            }
            if ("hitl_request".equals(e.getName())) {
                return format("hitl", e.getValue());
            }
            if ("push".equals(e.getName())) {
                return format("push", e.getValue());
            }
            if ("plan".equals(e.getName())) {
                return format("plan", e.getValue());
            }
            if ("stats".equals(e.getName())) {
                return format("stats", e.getValue());
            }
            // 未知自定义事件 → data 透传
            return format("data", mapOf("type", e.getName(), "data", e.getValue()));
        }

        // ─── HITL 确认 ───
        if (event instanceof RequireUserConfirmEvent e) {
            return format("hitl", mapOf(
                    "toolCalls", e.getToolCalls() == null ? null : e.getToolCalls().toString()));
        }
        if (event instanceof UserConfirmResultEvent e) {
            return format("data", mapOf(
                    "type", "user_confirm_result",
                    "results", e.getConfirmResults() == null ? null : e.getConfirmResults().toString()));
        }

        // ─── 子 agent / 外部执行 ───
        if (event instanceof SubagentExposedEvent e) {
            return format("data", mapOf(
                    "type", "subagent_exposed",
                    "subagentId", e.getSubagentId(),
                    "label", e.getLabel()));
        }
        if (event instanceof RequireExternalExecutionEvent e) {
            return format("data", mapOf(
                    "type", "require_external_execution",
                    "toolCalls", e.getToolCalls() == null ? null : e.getToolCalls().toString()));
        }
        if (event instanceof ExternalExecutionResultEvent e) {
            return format("data", mapOf(
                    "type", "external_execution_result",
                    "toolResults", e.getToolResults() == null ? null : e.getToolResults().toString()));
        }
        if (event instanceof AllToolsDeniedEvent e) {
            return format("error", mapOf(
                    "error", "所有工具调用被拒绝",
                    "denied", e.getDeniedToolCalls() == null ? null : e.getDeniedToolCalls().toString()));
        }

        // ─── 其余按 type 兜底分发 ───
        switch (type) {
            case AGENT_START -> {
                if (event instanceof AgentStartEvent e) {
                    return format("session", mapOf(
                            "name", e.getName(),
                            "role", e.getRole()));
                }
                return format("session", mapOf());
            }
            case AGENT_END -> {
                return format("stop", mapOf());
            }
            case REQUEST_STOP -> {
                return format("stop", mapOf());
            }
            case EXCEED_MAX_ITERS -> {
                return format("error", mapOf("error", "Agent 达到最大迭代次数"));
            }
            default -> {
                // 其余事件静默跳过 (文档 §10.2: return null)
                return null;
            }
        }
    }

    private String getProgressHint(String toolName) {
        if (toolName == null) {
            return null;
        }
        return TOOL_PROGRESS_HINTS.get(toolName);
    }

    /** SSE 帧格式：event: type\ndata: json\n\n */
    private String format(String eventType, Map<String, Object> data) {
        try {
            return "event: " + eventType + "\n" +
                    "data: " + objectMapper.writeValueAsString(data) + "\n\n";
        } catch (JsonProcessingException e) {
            return "event: error\ndata: {\"error\":\"SSE 序列化失败\"}\n\n";
        }
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ═══════════════════ PII 脱敏 (§7.5) ═══════════════════

    private static final java.util.regex.Pattern PII_PHONE =
            java.util.regex.Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final java.util.regex.Pattern PII_ID_CARD =
            java.util.regex.Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    private static final java.util.regex.Pattern PII_EMAIL =
            java.util.regex.Pattern.compile("\\b[\\w.-]+@[\\w.-]+\\.\\w{2,}\\b");

    /**
     * PII 脱敏 (复刻自文档 §7.5 maskPii)。
     * <ul>
     *   <li>手机号 13888888888 → 138****8888</li>
     *   <li>身份证 → 前6位 + ******** + 后4位</li>
     *   <li>邮箱 a.b@c.com → a***@c.com</li>
     * </ul>
     */
    public static String maskPii(String text) {
        if (text == null) {
            return null;
        }
        text = PII_PHONE.matcher(text).replaceAll(m -> {
            String s = m.group();
            return s.substring(0, 3) + "****" + s.substring(7);
        });
        text = PII_ID_CARD.matcher(text).replaceAll(m -> {
            String s = m.group();
            return s.substring(0, 6) + "********" + s.substring(14);
        });
        text = PII_EMAIL.matcher(text).replaceAll(m -> {
            String s = m.group();
            int at = s.indexOf('@');
            return s.charAt(0) + "***" + s.substring(at);
        });
        return text;
    }
}
