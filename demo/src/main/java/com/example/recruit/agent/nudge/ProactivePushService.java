package com.example.recruit.agent.nudge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动推送服务 (复刻自文档 §二 nudge/ProactivePushService)。
 *
 * <p>为每个用户维护一个 Reactor Sink，EventController 订阅该 Sink 实现 SSE 推送。
 * 记忆巩固/偏好变化/重要候选人等事件触发推送。
 */
@Service
public class ProactivePushService {

    private static final Logger log = LoggerFactory.getLogger(ProactivePushService.class);

    private final ConcurrentHashMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    /** 订阅某用户的推送流 (SSE)。 */
    public Flux<String> subscribe(String userId) {
        Sinks.Many<String> sink = sinks.computeIfAbsent(userId, k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    /** 向某用户推送一条消息 (SSE 帧)。 */
    public void push(String userId, String eventType, String dataJson) {
        Sinks.Many<String> sink = sinks.get(userId);
        if (sink == null) {
            log.debug("no sink for user {}, push dropped", userId);
            return;
        }
        String frame = "event: " + eventType + "\ndata: " + dataJson + "\n\n";
        sink.tryEmitNext(frame);
    }

    /** 推送一条简单文本消息。 */
    public void pushMessage(String userId, String message) {
        push(userId, "push", "{\"message\":\"" + escape(message) + "\"}");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
