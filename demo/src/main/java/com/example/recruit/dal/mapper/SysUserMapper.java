package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
