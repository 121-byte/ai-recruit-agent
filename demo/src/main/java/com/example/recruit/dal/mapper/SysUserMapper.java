package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户表 Mapper。含按用户名查询、带角色查询、更新与状态变更。
 *
 * <p>注: sys_user 表无 last_login_at 列 (见 schema_auth.sql), 故未实现 updateLastLoginAt。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名查询用户。
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);

    /**
     * 按主键查询用户 (join 角色表, 返回 SysUser, 角色可单独查询)。
     */
    @Select("SELECT u.* FROM sys_user u WHERE u.id = #{id}")
    SysUser findWithRolesById(@Param("id") Long id);

    /**
     * 查询全部用户。
     */
    @Select("SELECT * FROM sys_user ORDER BY id")
    List<SysUser> selectAll();

    /**
     * 更新用户 (全字段覆盖)。
     */
    @Update("UPDATE sys_user SET username = #{username}, password = #{password}, " +
            "real_name = #{realName}, email = #{email}, phone = #{phone}, " +
            "department = #{department}, status = #{status} WHERE id = #{id}")
    int update(SysUser user);

    /**
     * 更新用户状态 (active/disabled)。
     */
    @Update("UPDATE sys_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                    @Param("status") String status);

    // 注: sys_user 表无 last_login_at 列 (schema_auth.sql 中未定义), 跳过 updateLastLoginAt。
}
