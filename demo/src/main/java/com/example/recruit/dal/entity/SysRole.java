package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色表 (schema_auth.sql)。code: HR/OPS。
 */
@Data
@TableName(value = "sys_role", autoResultMap = true)
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** HR / OPS */
    private String code;

    private String name;

    private String description;
}
