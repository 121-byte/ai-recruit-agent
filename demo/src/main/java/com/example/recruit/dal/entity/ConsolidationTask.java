package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.recruit.dal.handler.PgArrayTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆巩固任务队列 (schema.sql)。status: pending/processing/completed/failed。
 */
@Data
@TableName(value = "consolidation_task", autoResultMap = true)
public class ConsolidationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String status;

    /** 待巩固的记忆条目 ID 集合 (BIGINT[]) */
    @TableField(typeHandler = PgArrayTypeHandler.class)
    private Long[] entryIds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode result;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
