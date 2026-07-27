package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息表 Mapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
