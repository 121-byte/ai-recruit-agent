package com.example.recruit.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE 事件 API (复刻自文档 §14.12)。
 *
 * <p>GET /api/events/{userId} SSE 事件订阅（推送通知）。
 *
 * <p>真实推送由 ProactivePushService 触发；此处为 Mock 实现，仅发射一个 session 事件。
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @GetMapping(value = "/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> events(@PathVariable String userId) {
        // 真实推送由 ProactivePushService 触发；这里仅返回会话建立事件。
        String sessionEvent = "event: session\ndata: {\"userId\":\"" + userId + "\"}\n\n";
        return Flux.just(sessionEvent).mergeWith(Flux.empty());
    }
}
