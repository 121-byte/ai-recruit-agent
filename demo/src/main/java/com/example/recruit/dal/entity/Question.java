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
 * 面试题表 (schema.sql)。type: technical/behavioral/project。
 */
@Data
@TableName(value = "question", autoResultMap = true)
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long interviewId;

    /** technical/behavioral/project */
    private String type;

    private String content;

    /** 追问题列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode followUps;

    private Boolean hrAdopted;

    private LocalDateTime createdAt;
}
