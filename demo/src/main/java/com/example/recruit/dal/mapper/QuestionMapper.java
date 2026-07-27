package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试题表 Mapper。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
