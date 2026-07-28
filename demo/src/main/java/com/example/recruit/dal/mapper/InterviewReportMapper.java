package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.InterviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 面试报告表 Mapper。含按面试 ID 列表查询与计数。
 */
@Mapper
public interface InterviewReportMapper extends BaseMapper<InterviewReport> {

    /**
     * 按面试 ID 列表查询报告 (idsSql 为已消毒的 "1,2,3" 字符串)。
     */
    @Select("SELECT * FROM interview_report WHERE interview_id IN (${idsSql})")
    List<InterviewReport> selectByInterviewIds(@Param("idsSql") String idsSql);

    /**
     * 统计报告总数。
     */
    @Select("SELECT COUNT(*) FROM interview_report")
    long count();
}
