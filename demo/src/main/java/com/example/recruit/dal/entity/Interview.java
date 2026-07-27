package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录表 (schema.sql)。status: pending/scheduled/completed/cancelled。
 */
@Data
@TableName(value = "interview", autoResultMap = true)
public class Interview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Long resumeId;

    private Integer round;

    /** pending/scheduled/completed/cancelled */
    private String status;

    private String interviewer;

    private LocalDateTime scheduledAt;

    private LocalDateTime createdAt;
}
