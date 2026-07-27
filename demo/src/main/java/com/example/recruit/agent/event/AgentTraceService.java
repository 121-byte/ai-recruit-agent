package com.example.recruit.agent.event;

import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.mapper.AgentTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Agent 追踪服务 —— 落库 {@link AgentTrace} (复刻自文档 §4.1 依赖 + §10.3)。
 *
 * <p>由 {@code ConversationAgentService} 在每轮 doOnComplete 中调用，
 * 记录 sessionId / agentName / stepType / tokens / latency / status。
 */
@Service
public class AgentTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    private final AgentTraceMapper agentTraceMapper;

    public AgentTraceService(AgentTraceMapper agentTraceMapper) {
        this.agentTraceMapper = agentTraceMapper;
    }

    public void record(String sessionId, String agentName, String stepType,
                       String toolName, String inputText, String outputText,
                       String model, int tokens, long latencyMs, String status) {
        AgentTrace trace = new AgentTrace();
        trace.setSessionId(sessionId);
        trace.setAgentName(agentName);
        trace.setStepNo(0);
        trace.setStepType(stepType);
        trace.setToolName(toolName);
        trace.setInputText(AgentTrace.truncate(inputText));
        trace.setOutputText(AgentTrace.truncate(outputText));
        trace.setModel(model);
        trace.setTokens(tokens);
        trace.setLatencyMs(latencyMs);
        trace.setStatus(status);
        trace.setCreatedAt(LocalDateTime.now());
        try {
            agentTraceMapper.insert(trace);
        } catch (Exception e) {
            log.debug("record trace failed: {}", e.getMessage());
        }
    }
}
