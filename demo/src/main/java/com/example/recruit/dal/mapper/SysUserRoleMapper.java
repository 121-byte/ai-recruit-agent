package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.SysUserRole;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联表 Mapper。复合主键 (user_id, role_id)。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 插入单条用户角色关联。
     */
    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId}) ON CONFLICT DO NOTHING")
    int insertOne(@Param("userId") Long userId,
                 @Param("roleId") Long roleId);

    /**
     * 批量插入用户角色关联 (循环单条 insert)。
     */
    default int insertBatch(List<SysUserRole> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (SysUserRole ur : list) {
            n += insertOne(ur.getUserId(), ur.getRoleId());
        }
        return n;
    }

    /**
     * 按用户 ID 查询其角色关联。
     */
    @Select("SELECT * FROM sys_user_role WHERE user_id = #{userId}")
    List<SysUserRole> selectByUserId(@Param("userId") Long userId);
}
