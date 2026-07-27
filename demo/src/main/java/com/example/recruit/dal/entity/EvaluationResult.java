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
 * 评估结果表 (schema.sql)。
 */
@Data
@TableName(value = "evaluation_result", autoResultMap = true)
public class EvaluationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sampleId;

    private String actualOutput;

    private Double score;

    /** 评估明细 (JSONB) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode detailsJson;

    private LocalDateTime createdAt;
}
