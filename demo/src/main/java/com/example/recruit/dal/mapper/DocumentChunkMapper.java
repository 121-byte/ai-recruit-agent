package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 文档语义分块表 Mapper。含 pgvector 分块级向量检索 (GROUP BY parent_id 去重, 对齐参考 §二-3)。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 按向量相似度检索指定 parent_type 的 top-K 分块 (GROUP BY parent_id 取最小 distance)。
     * 对齐参考: 返回 List&lt;Map&gt; (含 parent_id, dist)。
     * queryVector 传 FloatVectorTypeHandler.literal(float[]) 得到的字面量字符串。
     */
    @Select("SELECT parent_id, MIN(embedding &lt;=&gt; #{queryVector}::vector) AS dist " +
            "FROM document_chunk WHERE parent_type = #{parentType} " +
            "GROUP BY parent_id ORDER BY dist LIMIT #{topK}")
    List<Map<String, Object>> searchByVector(@Param("queryVector") String queryVector,
                                              @Param("parentType") String parentType,
                                              @Param("topK") int topK);

    /**
     * 按向量相似度检索 + JOIN resume 按 intended_position 多模式过滤 (对齐参考 §二-3)。
     * 返回 List&lt;Map&gt; (含 parent_id, dist)。
     * filtersCsv 为已消毒的 "'Java','后端'" 形式字符串 (调用方负责转义)。
     */
    @Select("<script>" +
            "SELECT dc.parent_id AS parent_id, MIN(dc.embedding &lt;=&gt; #{queryVector}::vector) AS dist " +
            "FROM document_chunk dc JOIN resume ON resume.id = dc.parent_id " +
            "WHERE dc.parent_type = #{parentType} " +
            "<if test='filtersCsv != null and filtersCsv != \"\"'>" +
            "AND (resume.parsed_json->>'intended_position' ILIKE #{filtersLike} " +
            "OR resume.raw_text ~* #{filtersRegex}) " +
            "</if>" +
            "GROUP BY dc.parent_id ORDER BY dist LIMIT #{topK}" +
            "</script>")
    List<Map<String, Object>> searchByVectorWithFilter(@Param("queryVector") String queryVector,
                                                         @Param("parentType") String parentType,
                                                         @Param("filtersCsv") String filtersCsv,
                                                         @Param("filtersLike") String filtersLike,
                                                         @Param("filtersRegex") String filtersRegex,
                                                         @Param("topK") int topK);

    /**
     * 批量插入分块 (循环单条 insert)。
     */
    default int batchInsert(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (DocumentChunk c : chunks) {
            n += insert(c);
        }
        return n;
    }

    /**
     * 按 parent_type + parent_id 查询所有分块。
     */
    @Select("SELECT * FROM document_chunk WHERE parent_type = #{parentType} AND parent_id = #{parentId} ORDER BY chunk_index")
    List<DocumentChunk> selectByParent(@Param("parentType") String parentType,
                                       @Param("parentId") Long parentId);

    /**
     * 统计指定 parent_type 的分块数。
     */
    @Select("SELECT COUNT(*) FROM document_chunk WHERE parent_type = #{parentType}")
    long countByParentType(@Param("parentType") String parentType);

    /**
     * 按 parent_type + parent_id 删除所有分块。
     */
    @Delete("DELETE FROM document_chunk WHERE parent_type = #{parentType} AND parent_id = #{parentId}")
    int deleteByParent(@Param("parentType") String parentType,
                      @Param("parentId") Long parentId);
}
