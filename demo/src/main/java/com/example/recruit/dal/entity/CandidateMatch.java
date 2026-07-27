package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 候选人匹配表 (schema.sql §3.1.3)。
 */
@Data
@TableName(value = "candidate_match", autoResultMap = true)
public class CandidateMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Long resumeId;

    private BigDecimal overallScore;

    private BigDecimal skillScore;

    private BigDecimal experienceScore;

    private BigDecimal softScore;

    private BigDecimal vectorScore;

    /** 匹配详情: LLM 评分理由 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode matchDetails;

    private String hrFeedback;

    private LocalDateTime createdAt;
}
