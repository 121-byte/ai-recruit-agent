package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆表 (schema.sql §3.1.4)。Agent ID 格式 {@code hr:{userId}}。
 */
@Data
@TableName(value = "memory_entry", autoResultMap = true)
public class MemoryEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Agent ID (格式 hr:{userId}) */
    private String agentId;

    private String memoryKey;

    private String memoryValue;

    /** preference/fact/note/archived */
    private String category;

    private String[] tags;

    private Integer accessCount;

    private LocalDateTime lastAccess;

    /** 重要性分数 0.0-1.0 */
    private Double importance;

    /** 记忆向量 (1024 维) */
    @TableField(typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;

    /** 动态 TTL 过期点: last_access + eff_half_life * ln(1/forget_threshold); 为空表示尚未预算 */
    private LocalDateTime ttlExpiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
