package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息表 (schema.sql)。role: user/assistant/system。
 */
@Data
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user/assistant/system */
    private String role;

    private String content;

    private Integer tokens;

    private LocalDateTime createdAt;
}
