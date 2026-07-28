package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Interview;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试记录表 Mapper。含按岗位/简历/状态查询与删除方法。
 */
@Mapper
public interface InterviewMapper extends BaseMapper<Interview> {

    /**
     * 按岗位 ID 查询面试记录。
     */
    @Select("SELECT * FROM interview WHERE job_id = #{jobId} ORDER BY created_at DESC")
    List<Interview> selectByJobId(@Param("jobId") Long jobId);

    /**
     * 按简历 ID 查询面试记录。
     */
    @Select("SELECT * FROM interview WHERE resume_id = #{resumeId} ORDER BY created_at DESC")
    List<Interview> selectByResumeId(@Param("resumeId") Long resumeId);

    /**
     * 按状态查询面试记录。
     */
    @Select("SELECT * FROM interview WHERE status = #{status} ORDER BY created_at DESC")
    List<Interview> selectByStatus(@Param("status") String status);

    /**
     * 按状态统计面试数。
     */
    @Select("SELECT COUNT(*) FROM interview WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 按简历 ID 删除面试记录。
     */
    @Delete("DELETE FROM interview WHERE resume_id = #{resumeId}")
    int deleteByResumeId(@Param("resumeId") Long resumeId);

    /**
     * 按岗位 ID 删除面试记录。
     */
    @Delete("DELETE FROM interview WHERE job_id = #{jobId}")
    int deleteByJobId(@Param("jobId") Long jobId);
}
