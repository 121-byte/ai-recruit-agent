package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 面试维度评分记录。 */
@Data
@TableName(value = "interview_evaluation", autoResultMap = true)
public class InterviewEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interviewId;
    private BigDecimal techScore;
    private BigDecimal projectScore;
    private BigDecimal commScore;
    private BigDecimal learningScore;
    private BigDecimal cultureScore;
    private String[] tags;
    private LocalDateTime createdAt;
}
