package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Interview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试记录表 Mapper。
 */
@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {
}
