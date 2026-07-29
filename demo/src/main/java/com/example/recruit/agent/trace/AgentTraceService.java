package com.example.recruit.agent.trace;

import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.mapper.AgentTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 追踪服务 (P4 归位: 合并原 agent/event/AgentTraceService 的写入 + AgentTraceReadService 的读取)。
 *
 * <p>由 ConversationAgentService 在每轮 doOnComplete 中调用 record/batchRecord 写入 agent_trace，
 * 由 Dashboard / chat/explain 调用只读统计方法。
 */
@Service
public class AgentTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceService.class);

    private final AgentTraceMapper agentTraceMapper;

    public AgentTraceService(AgentTraceMapper agentTraceMapper) {
        this.agentTraceMapper = agentTraceMapper;
    }

    // ─────────────────── 写入 ───────────────────

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

    public void batchRecord(List<AgentTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return;
        }
        try {
            agentTraceMapper.batchInsert(traces);
        } catch (Exception e) {
            log.debug("batchRecord trace failed: {}", e.getMessage());
        }
    }

    // ─────────────────── 读取 ───────────────────

    public List<AgentTrace> getSessionTrace(String sessionId) {
        try {
            return agentTraceMapper.selectBySessionId(sessionId);
        } catch (Exception e) {
            log.warn("getSessionTrace failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<AgentTrace> listByAgent(String agentName) {
        try {
            return agentTraceMapper.selectByAgentName(agentName);
        } catch (Exception e) {
            log.warn("listByAgent failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long countByAgent(String agentName) {
        try {
            return agentTraceMapper.countByAgentName(agentName);
        } catch (Exception e) {
            return 0;
        }
    }

    public long getAllSessionCount() {
        try {
            return agentTraceMapper.countDistinctSessions();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getSessionsWithToolCalls() {
        try {
            return agentTraceMapper.countSessionsWithToolCalls();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getCompletedSessions() {
        try {
            return agentTraceMapper.countCompletedSessions();
        } catch (Exception e) {
            return 0;
        }
    }
}
