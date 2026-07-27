package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话表 Mapper。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
