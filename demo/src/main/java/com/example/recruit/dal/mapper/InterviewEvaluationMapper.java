package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.InterviewEvaluation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 面试评估表 Mapper。含按面试 ID 查询与删除。
 */
@Mapper
public interface InterviewEvaluationMapper extends BaseMapper<InterviewEvaluation> {

    /**
     * 按面试 ID 查询评估记录。
     */
    @Select("SELECT * FROM interview_evaluation WHERE interview_id = #{interviewId} ORDER BY created_at DESC")
    InterviewEvaluation selectByInterviewId(@Param("interviewId") Long interviewId);

    /**
     * 按面试 ID 删除评估记录。
     */
    @Delete("DELETE FROM interview_evaluation WHERE interview_id = #{interviewId}")
    int deleteByInterviewId(@Param("interviewId") Long interviewId);
}
