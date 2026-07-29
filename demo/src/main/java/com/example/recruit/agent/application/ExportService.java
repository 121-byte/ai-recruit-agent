package com.example.recruit.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.entity.ChatMessage;
import com.example.recruit.dal.mapper.AgentTraceMapper;
import com.example.recruit.dal.mapper.ChatMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话导出服务 (复刻对齐清单 §2)。
 * 聚合 AgentTrace + ChatMessage, 拼接为可读文本导出。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final AgentTraceMapper agentTraceMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ExportService(AgentTraceMapper agentTraceMapper, ChatMessageMapper chatMessageMapper) {
        this.agentTraceMapper = agentTraceMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    /**
     * 导出会话全部内容 (trace 步骤 + chat 消息) 为文本。
     * sessionId 对应 agent_trace.session_id (字符串) 与 chat_message.session_id (数值)。
     */
    public String exportSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("===== Session Export: ").append(sessionId).append(" =====\n\n");

        // 1. Agent 追踪步骤
        List<AgentTrace> traces;
        try {
            traces = agentTraceMapper.selectBySessionId(sessionId);
        } catch (Exception e) {
            log.warn("exportSession traces failed: {}", e.getMessage());
            traces = List.of();
        }
        sb.append("----- Agent Traces (").append(traces.size()).append(") -----\n");
        for (AgentTrace t : traces) {
            sb.append("[#").append(t.getStepNo() == null ? 0 : t.getStepNo())
                    .append("] ").append(t.getAgentName() == null ? "" : t.getAgentName())
                    .append(" / ").append(t.getStepType() == null ? "" : t.getStepType());
            if (t.getToolName() != null && !t.getToolName().isBlank()) {
                sb.append(" / tool=").append(t.getToolName());
            }
            if (t.getTokens() != null) {
                sb.append(" / tokens=").append(t.getTokens());
            }
            if (t.getLatencyMs() != null) {
                sb.append(" / latency=").append(t.getLatencyMs()).append("ms");
            }
            sb.append(" / status=").append(t.getStatus() == null ? "" : t.getStatus()).append("\n");
            if (t.getInputText() != null && !t.getInputText().isBlank()) {
                sb.append("  IN : ").append(t.getInputText()).append("\n");
            }
            if (t.getOutputText() != null && !t.getOutputText().isBlank()) {
                sb.append("  OUT: ").append(t.getOutputText()).append("\n");
            }
        }
        sb.append("\n");

        // 2. 聊天消息 (chat_message.session_id 为数值, 尝试解析)
        List<ChatMessage> messages;
        try {
            Long sessionLong = parseLongSafe(sessionId);
            if (sessionLong != null) {
                messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionLong)
                        .orderByAsc(ChatMessage::getId));
            } else {
                messages = List.of();
            }
        } catch (Exception e) {
            log.warn("exportSession messages failed: {}", e.getMessage());
            messages = List.of();
        }
        sb.append("----- Chat Messages (").append(messages.size()).append(") -----\n");
        for (ChatMessage m : messages) {
            sb.append("[").append(m.getRole() == null ? "" : m.getRole()).append("] ");
            if (m.getTokens() != null) {
                sb.append("(tokens=").append(m.getTokens()).append(") ");
            }
            sb.append(m.getContent() == null ? "" : m.getContent()).append("\n");
        }
        sb.append("\n===== End of Export =====\n");
        return sb.toString();
    }

    /** 安全解析 Long, 失败返回 null。 */
    private Long parseLongSafe(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
