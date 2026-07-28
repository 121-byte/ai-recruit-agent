package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 聊天消息表 Mapper。含 token 求和聚合方法。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按会话 ID 汇总 token 数 (无记录返回 0)。
     */
    @Select("SELECT COALESCE(SUM(tokens), 0) FROM chat_message WHERE session_id = #{sessionId}")
    long sumTokensBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按 Agent ID 汇总 token 数 (join chat_session 关联 agent_id)。
     */
    @Select("SELECT COALESCE(SUM(m.tokens), 0) FROM chat_message m " +
            "JOIN chat_session s ON m.session_id = s.id WHERE s.agent_id = #{agentId}")
    long sumTokensByAgentId(@Param("agentId") String agentId);

    /**
     * 按会话 ID 统计消息数。
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE session_id = #{sessionId}")
    long countBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 按会话 ID 删除全部消息。
     */
    @Delete("DELETE FROM chat_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
