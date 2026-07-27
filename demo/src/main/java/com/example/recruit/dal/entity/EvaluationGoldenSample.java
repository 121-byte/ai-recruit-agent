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
 * 评估金标样本表 (schema.sql)。
 */
@Data
@TableName(value = "evaluation_golden_sample", autoResultMap = true)
public class EvaluationGoldenSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;

    private String inputText;

    private String expectedOutput;

    /** 评估标准 (JSONB) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode criteriaJson;

    private LocalDateTime createdAt;
}
