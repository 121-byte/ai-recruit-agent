package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.MemoryEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆表 Mapper。含 pgvector 向量检索与记忆衰减自定义方法。
 */
@Mapper
public interface MemoryEntryMapper extends BaseMapper<MemoryEntry> {

    /**
     * 按向量相似度 (cosine distance) 检索 top-K 记忆。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} ORDER BY embedding <=> #{queryVector}::vector LIMIT #{topK}")
    List<MemoryEntry> searchByVector(@Param("agentId") String agentId,
                                    @Param("queryVector") String queryVector,
                                    @Param("topK") int topK);

    /**
     * 按关键词模糊检索记忆 (ILIKE)。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} AND memory_value ILIKE '%' || #{keyword} || '%' LIMIT 10")
    List<MemoryEntry> searchByKeyword(@Param("agentId") String agentId,
                                      @Param("keyword") String keyword);

    /**
     * 对低重要性且长期未更新的记忆应用衰减因子。
     */
    @Update("UPDATE memory_entry SET importance = importance * #{factor} WHERE agent_id = #{agentId} AND importance < 0.7 AND updated_at < #{cutoff}")
    int applyDecay(@Param("agentId") String agentId,
                   @Param("factor") double factor,
                   @Param("cutoff") LocalDateTime cutoff);

    /**
     * 将低于阈值的记忆归档为 archived。
     */
    @Update("UPDATE memory_entry SET category = 'archived' WHERE agent_id = #{agentId} AND importance < #{threshold}")
    int archiveLowImportance(@Param("agentId") String agentId,
                             @Param("threshold") double threshold);

    /**
     * 删除重要性最低的 N 条记忆 (LRU 策略)。
     */
    @Delete("DELETE FROM memory_entry WHERE id IN (SELECT id FROM memory_entry WHERE agent_id = #{agentId} ORDER BY COALESCE(importance,0.5) ASC, updated_at ASC LIMIT #{limit})")
    int deleteLowest(@Param("agentId") String agentId,
                     @Param("limit") int limit);
}
