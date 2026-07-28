package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 面试对话表 Mapper。含消息整体替换与活跃会话查询。
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {

    /**
     * 整体替换 messages 字段 (jsonb) 并刷新 updated_at。
     * messages 参数需为合法 JSON 字符串。
     */
    @Update("UPDATE interview_session SET messages = #{messages}::jsonb, updated_at = now() WHERE id = #{id}")
    int appendMessage(@Param("id") Long id,
                      @Param("messages") String messages);

    /**
     * 查询最近一天内有更新的活跃会话。
     */
    @Select("SELECT * FROM interview_session WHERE updated_at &gt; now() - interval '1 day' ORDER BY updated_at DESC")
    List<InterviewSession> selectActiveSessions();
}
