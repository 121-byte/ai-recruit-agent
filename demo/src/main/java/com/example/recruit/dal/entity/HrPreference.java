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
 * HR 偏好表 (schema.sql)。hr_id 为主键 (IdType.INPUT)。
 */
@Data
@TableName(value = "hr_preference", autoResultMap = true)
public class HrPreference {

    @TableId(type = IdType.INPUT)
    private Long hrId;

    /** 偏好数据 (JSONB) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode preferenceJson;

    private LocalDateTime expireAt;

    private LocalDateTime updatedAt;
}
