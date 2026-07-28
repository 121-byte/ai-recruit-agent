package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档语义分块表 Mapper。含 pgvector 向量检索与按 parent 维护方法。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 按向量相似度检索指定 parent_type 的 top-K 分块 (pgvector cosine distance)。
     * queryVector 传 FloatVectorTypeHandler.literal(float[]) 得到的字面量。
     */
    @Select("SELECT * FROM document_chunk WHERE parent_type = #{parentType} " +
            "ORDER BY embedding &lt;=&gt; #{queryVector}::vector LIMIT #{topK}")
    List<DocumentChunk> searchByVector(@Param("queryVector") String queryVector,
                                       @Param("parentType") String parentType,
                                       @Param("topK") int topK);

    /**
     * 按向量相似度检索并可选按 chunk_type 过滤 (动态 SQL)。
     */
    @Select("<script>" +
            "SELECT * FROM document_chunk WHERE parent_type = #{parentType} " +
            "<if test='chunkType != null and chunkType != \"\"'>AND chunk_type = #{chunkType} </if>" +
            "ORDER BY embedding &lt;=&gt; #{queryVector}::vector LIMIT #{topK}" +
            "</script>")
    List<DocumentChunk> searchByVectorWithFilter(@Param("queryVector") String queryVector,
                                                 @Param("parentType") String parentType,
                                                 @Param("chunkType") String chunkType,
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
