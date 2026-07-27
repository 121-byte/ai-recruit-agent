package com.example.recruit.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 运行事件发布器 (复刻自文档 §10.1 AgentEventPublisher)。
 *
 * <p>将 AgentScope 的 {@link AgentEvent} 包装为 Spring 应用事件，发布到事件总线，
 * 用于解耦的异步处理（trace 记录、统计分析、推送）。监听方通过 @EventListener 订阅。
 */
@Component
public class AgentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AgentEventPublisher.class);

    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** 发布原始 AgentEvent (透传 + 标注 sessionId)。 */
    public void publish(AgentEvent event, String sessionId) {
        try {
            publisher.publishEvent(new AgentRunEvent(event, sessionId));
        } catch (Exception e) {
            log.debug("publish event failed: {}", e.getMessage());
        }
    }

    /** 发布自定义推送事件 (供 ProactivePushService 使用)。 */
    public void publishPush(String userId, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("message", message);
        data.put("ts", System.currentTimeMillis());
        try {
            publisher.publishEvent(new PushEvent(data));
        } catch (Exception e) {
            log.debug("publish push failed: {}", e.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Agent 运行事件载体。 */
    public record AgentRunEvent(AgentEvent event, String sessionId) {}

    /** 推送事件载体。 */
    public record PushEvent(Map<String, Object> data) {}
}
