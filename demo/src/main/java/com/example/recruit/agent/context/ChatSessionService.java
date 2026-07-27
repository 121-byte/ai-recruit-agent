package com.example.recruit.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.ChatMessage;
import com.example.recruit.dal.entity.ChatSession;
import com.example.recruit.dal.mapper.ChatMessageMapper;
import com.example.recruit.dal.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话 CRUD (复刻自文档 §二 context/ChatSessionService)。
 *
 * <p>支持会话列表/创建/删除/消息读取/token 统计。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatSessionService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    public List<ChatSession> listSessions(Long userId) {
        try {
            return sessionMapper.selectList(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getUserId, userId)
                            .orderByDesc(ChatSession::getUpdatedAt));
        } catch (Exception e) {
            log.warn("listSessions failed: {}", e.getMessage());
            return List.of();
        }
    }

    public ChatSession createSession(Long userId, String title, String agentId) {
        ChatSession s = new ChatSession();
        s.setUserId(userId);
        s.setTitle(title == null ? "新对话" : title);
        s.setAgentId(agentId);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        try {
            sessionMapper.insert(s);
        } catch (Exception e) {
            log.warn("createSession failed: {}", e.getMessage());
        }
        return s;
    }

    public void deleteSession(Long sessionId) {
        try {
            sessionMapper.deleteById(sessionId);
        } catch (Exception e) {
            log.warn("deleteSession failed: {}", e.getMessage());
        }
    }

    public List<ChatMessage> getMessages(Long sessionId) {
        try {
            return messageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId)
                            .orderByAsc(ChatMessage::getCreatedAt));
        } catch (Exception e) {
            log.warn("getMessages failed: {}", e.getMessage());
            return List.of();
        }
    }

    public ChatMessage saveMessage(Long sessionId, String role, String content, Integer tokens) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setTokens(tokens);
        m.setCreatedAt(LocalDateTime.now());
        try {
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("saveMessage failed: {}", e.getMessage());
        }
        return m;
    }

    /** 会话 token 统计。 */
    public Map<String, Object> tokenStats(Long sessionId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            List<ChatMessage> msgs = getMessages(sessionId);
            int total = msgs.stream().mapToInt(m -> m.getTokens() == null ? 0 : m.getTokens()).sum();
            stats.put("session_id", sessionId);
            stats.put("message_count", msgs.size());
            stats.put("total_tokens", total);
        } catch (Exception e) {
            stats.put("session_id", sessionId);
            stats.put("total_tokens", 0);
        }
        return stats;
    }
}
