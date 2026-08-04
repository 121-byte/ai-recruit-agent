package com.example.recruit.module.system.api;

import com.example.recruit.agent.nudge.ProactivePushService;
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
 * <p>订阅流先发射一个 session 建立事件, 再接入 ProactivePushService 的推送流,
 * 后者由记忆巩固/偏好变化/重要候选人等事件触发。
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final ProactivePushService proactivePushService;

    public EventController(ProactivePushService proactivePushService) {
        this.proactivePushService = proactivePushService;
    }

    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> subscribe(@PathVariable String userId) {
        // 先发会话建立事件, 再接入 ProactivePushService 的真实推送流 (Sink 永不完成, SSE 保持长连接)。
        String sessionEvent = "event: session\ndata: {\"userId\":\"" + userId + "\"}\n\n";
        return Flux.just(sessionEvent).concatWith(proactivePushService.subscribe(userId));
    }

    /** GET /active —— 在线/活跃状态 (简化: 返回 {active:true})。 */
    @GetMapping("/active")
    public Map<String, Object> active() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("active", true);
        return resp;
    }
}
