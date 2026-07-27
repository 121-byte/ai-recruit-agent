package com.example.recruit.agent.context;

import io.agentscope.core.agent.RuntimeContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope 会话管理 (复刻自文档 §二 context/SessionManager)。
 *
 * <p>管理 {@link RuntimeContext} 的获取/创建。每个 sessionId 对应一个 RuntimeContext，
 * Agent 调用时通过该上下文传递 sessionId / userId / memorySnapshot。
 */
@Component
public class SessionManager {

    private final ConcurrentHashMap<String, RuntimeContext> sessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建 RuntimeContext。
     */
    public RuntimeContext getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId == null ? "default" : sessionId, id -> {
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(id)
                    .build();
            return ctx;
        });
    }

    public RuntimeContext get(String sessionId) {
        return sessions.get(sessionId == null ? "default" : sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId == null ? "default" : sessionId);
    }
}
