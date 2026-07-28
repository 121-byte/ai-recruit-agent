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

    /**
     * 多条件搜索/筛选岗位。
     */
    @Select("<script>" +
            "SELECT * FROM job_profile WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>AND (" +
            "  title ILIKE '%' || #{keyword} || '%'" +
            "  OR department ILIKE '%' || #{keyword} || '%'" +
            "  OR location ILIKE '%' || #{keyword} || '%'" +
            "  OR level ILIKE '%' || #{keyword} || '%'" +
            "  OR category ILIKE '%' || #{keyword} || '%'" +
            ")</if>" +
            "<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
            "<if test='department != null and department != \"\"'>AND department = #{department} </if>" +
            "<if test='level != null and level != \"\"'>AND level = #{level} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<JobProfile> search(@Param("keyword") String keyword,
                            @Param("status") String status,
                            @Param("department") String department,
                            @Param("level") String level);

    /**
     * 查询所有不重复的部门列表。
     */
    @Select("SELECT DISTINCT department FROM job_profile WHERE department IS NOT NULL AND department != '' ORDER BY department")
    List<String> listDepartments();

    /**
     * 查询所有不重复的职级列表。
     */
    @Select("SELECT DISTINCT level FROM job_profile WHERE level IS NOT NULL AND level != '' ORDER BY level")
    List<String> listLevels();
}
