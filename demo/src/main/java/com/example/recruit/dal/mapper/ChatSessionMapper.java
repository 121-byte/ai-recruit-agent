package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ChatSession;
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
}
