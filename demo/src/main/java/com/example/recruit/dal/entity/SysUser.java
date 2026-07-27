package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 (schema_auth.sql)。password 字段为 BCrypt 哈希。
 */
@Data
@TableName(value = "sys_user", autoResultMap = true)
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希 */
    private String password;

    private String realName;

    private String email;

    private String phone;

    private String department;

    /** active/disabled */
    private String status;

    private LocalDateTime createdAt;
}
