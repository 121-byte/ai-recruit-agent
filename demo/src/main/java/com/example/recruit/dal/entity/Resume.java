package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历表 (schema.sql §3.1.1)。
 */
@Data
@TableName(value = "resume", autoResultMap = true)
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String candidateName;

    private String rawText;

    /** LLM 解析后的结构化数据: skills/work_experience/education/intended_position/work_years 等 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode parsedJson;

    /** 简历全文向量 (1024 维) */
    @TableField(typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;

    private String[] riskTags;

    private String status;   // pending/reviewed/rejected

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
