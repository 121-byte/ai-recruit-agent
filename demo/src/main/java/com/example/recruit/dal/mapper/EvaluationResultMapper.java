package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.EvaluationResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 评估结果表 Mapper。含按 category 聚合平均分。
 */
@Mapper
public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {

    /**
     * 按金标样本 category 聚合平均分 (join evaluation_golden_sample)。
     * 返回每行 {category, avg_score}。
     */
    @Select("SELECT s.category AS category, AVG(r.score) AS avg_score " +
            "FROM evaluation_result r JOIN evaluation_golden_sample s ON r.sample_id = s.id " +
            "GROUP BY s.category")
    List<Map<String, Object>> avgScoreByCategory();

    /**
     * 按金标样本 ID 查询评估结果。
     */
    @Select("SELECT * FROM evaluation_result WHERE sample_id = #{sampleId} ORDER BY created_at DESC")
    List<EvaluationResult> selectBySampleId(@Param("sampleId") Long sampleId);
}
