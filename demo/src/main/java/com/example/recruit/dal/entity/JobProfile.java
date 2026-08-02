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
 * 岗位画像表 (schema.sql §3.1.2)。
 */
@Data
@TableName(value = "job_profile", autoResultMap = true)
public class JobProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String jdText;

    private String department;

    private String level;

    private String location;

    private Integer salaryMin;

    private Integer salaryMax;

    private Integer experienceMin;

    private Integer experienceMax;

    private String education;

    private Integer headcount;

    private String category;

    /**
     * 岗位结构化分析结果 (镜像简历 structuredData: positionInfo/skills/responsibilities/
     * projectContext/education/certifications/requirements/roleGraph/growthPath)。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode parsedJson;

    /** 岗位向量 (1024 维) */
    @TableField(typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;

    private String status;   // draft/active/closed

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
