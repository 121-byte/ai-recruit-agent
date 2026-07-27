package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 面试对话表 (schema.sql)。
 */
@Data
@TableName(value = "interview_session", autoResultMap = true)
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long interviewId;

    /** 对话消息列表 (JSONB) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode messages;

    private Integer currentRound;

    /** 难度等级: easy/medium/hard */
    private String difficultyLevel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
