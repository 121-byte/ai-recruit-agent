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

    // === 独立列 (schema.sql 增量迁移, 正则快速提取 + LLM 回写) ===
    private String phone;
    private String email;
    private String education;
    private String school;
    private String major;
    private Integer yearsExperience;
    private String intendedPosition;

    private String rawText;

    /** LLM 4+1 轮完整分析结果 (structuredData/implicitInsights/riskAssessment/potentialAssessment/validation) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode parsedJson;

    /** 简历全文向量 (1024 维) */
    @TableField(typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;

    private String[] riskTags;

    private String status;   // pending/parsed/analyzed/reviewed/rejected

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
