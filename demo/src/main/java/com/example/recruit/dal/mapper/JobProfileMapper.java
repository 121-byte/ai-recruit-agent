package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.JobProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 岗位画像表 Mapper。含向量更新与按状态过滤方法。
 */
@Mapper
public interface JobProfileMapper extends BaseMapper<JobProfile> {

    /**
     * 更新岗位向量 (embedding 列)。embedding 传字面量字符串。
     */
    @Update("UPDATE job_profile SET embedding = #{embedding}::vector WHERE id = #{id}")
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") String embedding);

    /**
     * 按状态可选过滤查询岗位 (动态 SQL)。
     */
    @Select("<script>" +
            "SELECT * FROM job_profile WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<JobProfile> selectByFilter(@Param("status") String status);
}
