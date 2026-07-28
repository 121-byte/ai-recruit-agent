package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.CandidateMatch;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 候选人匹配表 Mapper。含 join 查询与按 resume/job 维护方法。
 */
@Mapper
public interface CandidateMatchMapper extends BaseMapper<CandidateMatch> {

    /**
     * 按岗位查询匹配记录并 join 简历的候选人姓名与解析数据 (返回投影 Map)。
     */
    @Select("SELECT cm.*, r.candidate_name, r.parsed_json " +
            "FROM candidate_match cm LEFT JOIN resume r ON cm.resume_id = r.id " +
            "WHERE cm.job_id = #{jobId} ORDER BY cm.overall_score DESC")
    List<Map<String, Object>> selectByJobIdWithResume(@Param("jobId") Long jobId);

    /**
     * 按岗位 + 简历查询匹配记录。
     */
    @Select("SELECT * FROM candidate_match WHERE job_id = #{jobId} AND resume_id = #{resumeId}")
    CandidateMatch selectByJobAndResume(@Param("jobId") Long jobId,
                                       @Param("resumeId") Long resumeId);

    /**
     * 按简历删除匹配记录。
     */
    @Delete("DELETE FROM candidate_match WHERE resume_id = #{resumeId}")
    int deleteByResumeId(@Param("resumeId") Long resumeId);

    /**
     * 按岗位删除匹配记录。
     */
    @Delete("DELETE FROM candidate_match WHERE job_id = #{jobId}")
    int deleteByJobId(@Param("jobId") Long jobId);

    /**
     * 统计匹配记录总数。
     */
    @Select("SELECT COUNT(*) FROM candidate_match")
    long count();
}
