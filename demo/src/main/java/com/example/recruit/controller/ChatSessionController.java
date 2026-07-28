package com.example.recruit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.dal.entity.ChatMessage;
import com.example.recruit.dal.entity.ChatSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理 API (复刻自对齐清单 §5.2, @RequestMapping("/api/agent/sessions"))。
 *
 * <p>GET    /                    列出当前用户会话
 * <p>POST   /                    创建会话
 * <p>DELETE /{id}                 删除会话
 * <p>GET    /{id}/messages        获取会话消息
 * <p>PUT    /{id}/title           更新会话标题
 * <p>GET    /tokens/summary        当前用户 token 聚合统计
 * <p>GET    /{id}/tokens           会话 token 统计
 */
@RestController
@RequestMapping("/api/agent/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? 0L : Long.parseLong(String.valueOf(loginId));
    }

    @GetMapping
    public List<ChatSession> list() {
        return chatSessionService.listSessions(currentUserId());
    }

    @PostMapping
    public ChatSession create(@RequestBody Map<String, Object> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        String agentId = body.get("agentId") == null ? null : String.valueOf(body.get("agentId"));
        return chatSessionService.createSession(currentUserId(), title, agentId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        chatSessionService.deleteSession(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("status", "ok");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessage> messages(@PathVariable Long id) {
        return chatSessionService.getMessages(id);
    }

    /** PUT /{id}/title —— 更新会话标题。 */
    @PutMapping("/{id}/title")
    public ResponseEntity<Map<String, Object>> updateTitle(@PathVariable Long id,
                                                            @RequestBody Map<String, String> body) {
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        boolean ok = chatSessionService.updateTitle(id, title);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("updated", ok);
        resp.put("title", title);
        return ResponseEntity.ok(resp);
    }

    /** GET /tokens/summary —— 当前用户全部会话 token 聚合统计。 */
    @GetMapping("/tokens/summary")
    public ResponseEntity<Map<String, Object>> getTokenSummary() {
        Long userId = currentUserId();
        List<ChatSession> sessions = chatSessionService.listSessions(userId);
        long totalTokens = 0;
        for (ChatSession s : sessions) {
            try {
                Map<String, Object> stats = chatSessionService.tokenStats(s.getId());
                Object t = stats.get("total_tokens");
                if (t instanceof Number n) {
                    totalTokens += n.longValue();
                }
            } catch (Exception ignored) {
                // 单会话统计失败不阻断聚合
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", userId);
        summary.put("session_count", sessions.size());
        summary.put("total_tokens", totalTokens);
        return ResponseEntity.ok(summary);
    }

    /** GET /{id}/tokens —— 会话 token 统计。 */
    @GetMapping("/{id}/tokens")
    public ResponseEntity<Map<String, Object>> getSessionTokens(@PathVariable Long id) {
        return ResponseEntity.ok(chatSessionService.tokenStats(id));
    }
}
