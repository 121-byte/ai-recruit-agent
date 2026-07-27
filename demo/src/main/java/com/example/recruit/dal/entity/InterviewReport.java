package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 面试报告表 (schema.sql)。
 */
@Data
@TableName(value = "interview_report", autoResultMap = true)
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long interviewId;

    private BigDecimal overallScore;

    private BigDecimal techScore;

    private BigDecimal commScore;

    private BigDecimal problemSolvingScore;

    private BigDecimal cultureFitScore;

    private String[] strengths;

    private String[] risks;

    private String hiringSuggestion;

    private String summary;

    private LocalDateTime createdAt;
}
