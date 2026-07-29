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
            // 聚合每个会话的累计 token 数 (token_count → tokenCount)
            return sessionMapper.listWithTokens(userId);
        } catch (Exception e) {
            log.warn("listSessions failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 刷新会话 updated_at, 让最近活跃会话上浮排序。 */
    public void touch(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            sessionMapper.touch(sessionId);
        } catch (Exception e) {
            log.warn("touch session failed: {}", e.getMessage());
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

    public boolean deleteSession(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        try {
            return sessionMapper.deleteByIdAndUserId(sessionId, userId) > 0;
        } catch (Exception e) {
            log.warn("deleteSession failed: {}", e.getMessage());
            return false;
        }
    }

    /** 更新会话标题 (对齐清单 §5.2 PUT /{id}/title)。 */
    public boolean updateTitle(Long sessionId, String title) {
        if (sessionId == null || title == null) {
            return false;
        }
        try {
            return sessionMapper.updateTitle(sessionId, title) > 0;
        } catch (Exception e) {
            log.warn("updateTitle failed: {}", e.getMessage());
            return false;
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

    public ChatMessage saveMessage(Long sessionId, String role, String content, String reasoning, Integer tokens) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setReasoning(reasoning);
        m.setTokens(tokens);
        m.setCreatedAt(LocalDateTime.now());
        try {
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("saveMessage failed: {}", e.getMessage());
        }
        return m;
    }

    /** HITL 的确认执行沿用原始轮次，仅补充该轮 assistant 的输出 token。 */
    public void addTokensToLatestAssistant(Long sessionId, int tokens) {
        if (sessionId == null || tokens <= 0) {
            return;
        }
        try {
            messageMapper.addTokensToLatestAssistant(sessionId, tokens);
            touch(sessionId);
        } catch (Exception e) {
            log.warn("add HITL assistant tokens failed: {}", e.getMessage());
        }
    }

    /** 会话 token 统计: total/input(role=user)/output(role=assistant)。 */
    public Map<String, Object> tokenStats(Long sessionId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("session_id", sessionId);
        try {
            long input = messageMapper.sumTokensBySessionAndRole(sessionId, "user");
            long output = messageMapper.sumTokensBySessionAndRole(sessionId, "assistant");
            stats.put("message_count", messageMapper.countBySessionId(sessionId));
            stats.put("input_tokens", input);
            stats.put("output_tokens", output);
            stats.put("total_tokens", input + output);
        } catch (Exception e) {
            stats.put("total_tokens", 0);
        }
        return stats;
    }
}
