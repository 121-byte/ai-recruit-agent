package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 简历表 Mapper。含向量更新与按状态过滤方法。
 */
@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 更新简历向量 (embedding 列)。embedding 传字面量字符串。
     */
    @Update("UPDATE resume SET embedding = #{embedding}::vector WHERE id = #{id}")
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") String embedding);

    /**
     * 按状态可选过滤查询简历 (动态 SQL)。
     */
    @Select("<script>" +
            "SELECT * FROM resume WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<Resume> selectByFilter(@Param("status") String status);

    /**
     * 按主键列表查询简历 (idsSql 为已消毒的 "1,2,3" 字符串)。
     */
    @Select("SELECT * FROM resume WHERE id IN (${idsSql})")
    List<Resume> selectByIds(@Param("idsSql") String idsSql);

    /**
     * 多条件搜索/筛选简历 (parsed_json->>intended_position 等 JSONB 字段)。
     */
    @Select("<script>" +
            "SELECT * FROM resume WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>AND (" +
            "  candidate_name ILIKE '%' || #{keyword} || '%'" +
            "  OR raw_text ILIKE '%' || #{keyword} || '%'" +
            "  OR parsed_json->>'skills' ILIKE '%' || #{keyword} || '%'" +
            "  OR parsed_json->>'intended_position' ILIKE '%' || #{keyword} || '%'" +
            "  OR parsed_json->>'school' ILIKE '%' || #{keyword} || '%'" +
            "  OR parsed_json->>'major' ILIKE '%' || #{keyword} || '%'" +
            ")</if>" +
            "<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
            "<if test='intendedPosition != null and intendedPosition != \"\"'>" +
            "  AND parsed_json->>'intended_position' = #{intendedPosition} </if>" +
            "<if test='education != null and education != \"\"'>" +
            "  AND parsed_json->>'education' = #{education} </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<Resume> search(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("intendedPosition") String intendedPosition,
                        @Param("education") String education);

    /**
     * 查询所有不重复的意向岗位 (parsed_json->>intended_position)。
     */
    @Select("SELECT DISTINCT parsed_json->>'intended_position' AS pos FROM resume WHERE parsed_json->>'intended_position' IS NOT NULL AND parsed_json->>'intended_position' != '' ORDER BY pos")
    List<String> listIntendedPositions();

    /**
     * 查询所有不重复的学历 (parsed_json->>education)。
     */
    @Select("SELECT DISTINCT parsed_json->>'education' AS edu FROM resume WHERE parsed_json->>'education' IS NOT NULL AND parsed_json->>'education' != '' ORDER BY edu")
    List<String> listEducations();

    /**
     * 按状态查询简历。
     */
    @Select("SELECT * FROM resume WHERE status = #{status} ORDER BY created_at DESC")
    List<Resume> selectByStatus(@Param("status") String status);

    /**
     * 统计简历总数。
     */
    @Select("SELECT COUNT(*) FROM resume")
    long count();

    /**
     * 按状态统计简历数。
     */
    @Select("SELECT COUNT(*) FROM resume WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 向量召回: 按查询向量余弦相似度 Top-K 召回简历 (pgvector {@code <=>} HNSW)。
     * queryVector 传 {@code FloatVectorTypeHandler.literal(float[])} 字面量字符串。
     */
    @Select("SELECT * FROM resume ORDER BY embedding &lt;=&gt; #{queryVector}::vector LIMIT #{topK}")
    List<Resume> searchByVector(@Param("queryVector") String queryVector,
                                  @Param("topK") int topK);

    /**
     * 向量召回 + 方向预过滤: parsed_json->>'intended_position' 任一匹配过滤词。
     * filtersCsv 为已消毒的 "'Java','后端'" 形式字符串 (调用方负责转义)。
     */
    @Select("<script>" +
            "SELECT * FROM resume WHERE embedding IS NOT NULL " +
            "<if test='filtersCsv != null and filtersCsv != \"\"'>" +
            "AND (parsed_json->>'intended_position' IN (${filtersCsv}) " +
            "OR raw_text ~* #{filtersRegex}) " +
            "</if>" +
            "ORDER BY embedding &lt;=&gt; #{queryVector}::vector LIMIT #{topK}" +
            "</script>")
    List<Resume> searchByVectorWithFilter(@Param("queryVector") String queryVector,
                                            @Param("filtersCsv") String filtersCsv,
                                            @Param("filtersRegex") String filtersRegex,
                                            @Param("topK") int topK);
}
