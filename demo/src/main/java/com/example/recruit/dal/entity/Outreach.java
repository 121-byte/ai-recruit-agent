package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候选人触达表 (schema.sql)。status: draft/sent/replied/ignored。
 */
@Data
@TableName(value = "outreach", autoResultMap = true)
public class Outreach {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Long resumeId;

    private String message;

    /** draft/sent/replied/ignored */
    private String status;

    private String batchId;

    private LocalDateTime createdAt;
}
