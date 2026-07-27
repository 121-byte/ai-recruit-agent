package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.ConsolidationTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 记忆巩固任务队列 Mapper。
 */
@Mapper
public interface ConsolidationTaskMapper extends BaseMapper<ConsolidationTask> {
}
