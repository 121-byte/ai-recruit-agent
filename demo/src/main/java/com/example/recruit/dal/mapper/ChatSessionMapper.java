package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天会话表 Mapper。含软删除(更新时间)、改标题与按 Agent 查询。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Delete("DELETE FROM chat_session WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 软删除 (无 deleted 列, 简化为刷新 updated_at 标记)。
     */
    @Update("UPDATE chat_session SET updated_at = now() WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    /**
     * 更新会话标题。
     */
    @Update("UPDATE chat_session SET title = #{title}, updated_at = now() WHERE id = #{id}")
    int updateTitle(@Param("id") Long id,
                   @Param("title") String title);

    /**
     * 按 Agent ID 查询会话。
     */
    @Select("SELECT * FROM chat_session WHERE agent_id = #{agentId} ORDER BY updated_at DESC")
    List<ChatSession> selectByAgentId(@Param("agentId") String agentId);

    /**
     * 列出用户会话并聚合每个会话的累计 token 数 (token_count)。
     * map-underscore-to-camel-case 将 token_count 映射到 ChatSession.tokenCount。
     */
    @Select("SELECT s.*, " +
            "COALESCE((SELECT SUM(m.tokens) FROM chat_message m WHERE m.session_id = s.id), 0) AS token_count " +
            "FROM chat_session s WHERE s.user_id = #{userId} ORDER BY s.updated_at DESC")
    List<ChatSession> listWithTokens(@Param("userId") Long userId);

    /**
     * 刷新会话 updated_at (让最近活跃会话上浮排序)。
     */
    @org.apache.ibatis.annotations.Update(
            "UPDATE chat_session SET updated_at = now() WHERE id = #{id}")
    int touch(@Param("id") Long id);
}
