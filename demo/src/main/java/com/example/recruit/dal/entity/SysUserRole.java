package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户角色关联表 (schema_auth.sql)。复合主键 (user_id, role_id)。
 */
@Data
@TableName(value = "sys_user_role", autoResultMap = true)
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
