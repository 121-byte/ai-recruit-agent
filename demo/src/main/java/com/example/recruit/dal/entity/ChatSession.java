package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话表 (schema.sql)。
 */
@Data
@TableName(value = "chat_session", autoResultMap = true)
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String agentId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
