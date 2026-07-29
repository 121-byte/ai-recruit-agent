package com.example.recruit.module.system.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 事件 API (复刻自对齐清单 §5.10, @RequestMapping("/api/events"))。
 *
 * <p>2 个权威端点:
 * <ul>
 *   <li>GET /subscribe/{userId}  SSE 事件订阅 (推送通知)</li>
 *   <li>GET /active              在线/活跃状态</li>
 * </ul>
 *
 * <p>真实推送由 ProactivePushService 触发; 此处为 Mock 实现, 仅发射一个 session 事件。
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> subscribe(@PathVariable String userId) {
        // 真实推送由 ProactivePushService 触发; 这里仅返回会话建立事件。
        String sessionEvent = "event: session\ndata: {\"userId\":\"" + userId + "\"}\n\n";
        return Flux.just(sessionEvent).mergeWith(Flux.empty());
    }

    /** GET /active —— 在线/活跃状态 (简化: 返回 {active:true})。 */
    @GetMapping("/active")
    public Map<String, Object> active() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("active", true);
        return resp;
    }
}
