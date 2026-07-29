package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.InterviewEvaluation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Interview evaluation persistence mapper. */
@Mapper
public interface InterviewEvaluationMapper extends BaseMapper<InterviewEvaluation> {

    @Select("SELECT * FROM interview_evaluation WHERE interview_id = #{interviewId}")
    InterviewEvaluation selectByInterviewId(@Param("interviewId") Long interviewId);

    @Delete("DELETE FROM interview_evaluation WHERE interview_id = #{interviewId}")
    int deleteByInterviewId(@Param("interviewId") Long interviewId);
}
