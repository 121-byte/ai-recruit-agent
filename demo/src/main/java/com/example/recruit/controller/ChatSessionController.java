package com.example.recruit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.recruit.agent.context.ChatSessionService;
import com.example.recruit.dal.entity.ChatMessage;
import com.example.recruit.dal.entity.ChatSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话管理 API (复刻自文档 §14.3)。
 *
 * <p>GET    /api/chat/sessions           列出会话
 * <p>POST   /api/chat/sessions           创建会话
 * <p>DELETE /api/chat/sessions/{id}      删除会话
 * <p>GET    /api/chat/sessions/{id}/messages 获取会话消息
 * <p>GET    /api/chat/sessions/{id}/tokens    获取会话 token 统计
 */
@RestController
@RequestMapping("/api/chat/sessions")
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
    public Map<String, Object> delete(@PathVariable Long id) {
        chatSessionService.deleteSession(id);
        return Map.of("status", "ok");
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessage> messages(@PathVariable Long id) {
        return chatSessionService.getMessages(id);
    }

    @GetMapping("/{id}/tokens")
    public Map<String, Object> tokens(@PathVariable Long id) {
        return chatSessionService.tokenStats(id);
    }
}
