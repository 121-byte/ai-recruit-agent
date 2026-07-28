package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.EvaluationGoldenSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评估金标样本表 Mapper。无 active 列, 全选即为活跃样本。
 */
@Mapper
public interface EvaluationGoldenSampleMapper extends BaseMapper<EvaluationGoldenSample> {

    /**
     * 查询全部金标样本 (无 active 列, 即活跃集合)。
     */
    @Select("SELECT * FROM evaluation_golden_sample ORDER BY created_at DESC")
    List<EvaluationGoldenSample> selectActive();

    /**
     * 按类别查询金标样本。
     */
    @Select("SELECT * FROM evaluation_golden_sample WHERE category = #{category} ORDER BY created_at DESC")
    List<EvaluationGoldenSample> selectByCategory(@Param("category") String category);
}
