package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历表 Mapper。
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
