package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 面试对话表 Mapper。
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
