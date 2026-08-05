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
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} ORDER BY embedding &lt;=&gt; #{queryVector}::vector LIMIT #{topK}")
    List<MemoryEntry> searchByVector(@Param("agentId") String agentId,
                                    @Param("queryVector") String queryVector,
                                    @Param("topK") int topK);

    /**
     * 按关键词模糊检索记忆 (ILIKE), 覆盖 key/value/tags, 过滤 TTL 已过期 (对齐 Hebb: 检索时不可见)。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} AND category != 'archived' " +
            "AND (ttl_expires_at IS NULL OR ttl_expires_at > NOW()) " +
            "AND (memory_key ILIKE '%' || #{keyword} || '%' " +
            "OR memory_value ILIKE '%' || #{keyword} || '%' " +
            "OR array_to_string(tags, ',') ILIKE '%' || #{keyword} || '%') LIMIT 10")
    List<MemoryEntry> searchByKeyword(@Param("agentId") String agentId,
                                      @Param("keyword") String keyword);

    /**
     * 批量续期: 命中检索的记忆 access_count+1、刷新 last_access, 并按艾宾浩斯留存曲线重算 ttl_expires_at。
     *
     * <p>eff_half_live(baseTtlSeconds) = halfLifeDays * ln(1/forgetThreshold) * 86400 (Java 侧预算),
     * 再乘 (1 + kImportance*importance + kAccess*min(accessCount,10)/10); 硬下限 created_at + minRetention。
     * 一条 SQL 原子完成, 避免并发丢失。
     */
    @Update("<script>" +
            "UPDATE memory_entry SET " +
            "access_count = access_count + 1, " +
            "last_access = NOW(), " +
            "ttl_expires_at = GREATEST(" +
            "  NOW() + (#{baseTtlSeconds} * (1 + #{kImportance} * COALESCE(importance, 0.5) " +
            "    + #{kAccess} * LEAST(COALESCE(access_count, 0), 10) / 10.0)) * INTERVAL '1 second', " +
            "  COALESCE(created_at, NOW()) + #{minRetentionSeconds} * INTERVAL '1 second' " +
            ") " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int renewAccess(@Param("ids") List<Long> ids,
                    @Param("baseTtlSeconds") double baseTtlSeconds,
                    @Param("kImportance") double kImportance,
                    @Param("kAccess") double kAccess,
                    @Param("minRetentionSeconds") long minRetentionSeconds);

    /**
     * TTL 清扫: 删除已过 TTL 且未被高重要度保护的记忆。
     */
    @Delete("DELETE FROM memory_entry " +
            "WHERE ttl_expires_at IS NOT NULL AND ttl_expires_at < NOW() " +
            "AND (importance IS NULL OR importance < #{protectThreshold})")
    int deleteExpired(@Param("protectThreshold") double protectThreshold);

    /**
     * 将低于阈值的记忆归档为 archived。
     */
    @Update("UPDATE memory_entry SET category = 'archived' WHERE agent_id = #{agentId} AND importance &lt; #{threshold}")
    int archiveLowImportance(@Param("agentId") String agentId,
                             @Param("threshold") double threshold);

    /**
     * 删除重要性最低的 N 条记忆 (LRU 策略)。
     */
    @Delete("DELETE FROM memory_entry WHERE id IN (SELECT id FROM memory_entry WHERE agent_id = #{agentId} ORDER BY COALESCE(importance,0.5) ASC, updated_at ASC LIMIT #{limit})")
    int deleteLowest(@Param("agentId") String agentId,
                     @Param("limit") int limit);

    /**
     * 按 agent_id 查询全部记忆。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} ORDER BY created_at DESC")
    List<MemoryEntry> findByAgentId(@Param("agentId") String agentId);

    /**
     * 按 agent_id + category 查询记忆。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} AND category = #{category} ORDER BY created_at DESC")
    List<MemoryEntry> findByAgentIdAndCategory(@Param("agentId") String agentId,
                                                @Param("category") String category);

    /**
     * 访问计数 +1 并刷新 last_access 时间。
     */
    @Update("UPDATE memory_entry SET access_count = access_count + 1, last_access = now() WHERE id = #{id}")
    int incrementAccessCount(@Param("id") Long id);

    /**
     * 更新单条记忆的重要性分数。
     */
    @Update("UPDATE memory_entry SET importance = #{importance} WHERE id = #{id}")
    int updateImportance(@Param("id") Long id,
                         @Param("importance") Double importance);

    /**
     * 按 agent_id + memory_key 删除记忆。
     */
    @Delete("DELETE FROM memory_entry WHERE agent_id = #{agentId} AND memory_key = #{memoryKey}")
    int deleteByAgentIdAndKey(@Param("agentId") String agentId,
                             @Param("memoryKey") String memoryKey);

    /**
     * 查询待巩固记忆: importance IS NULL OR importance=0.5, ORDER BY created_at ASC, LIMIT n。
     */
    @Select("SELECT * FROM memory_entry WHERE agent_id = #{agentId} " +
            "AND (importance IS NULL OR importance = 0.5) " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<MemoryEntry> findPendingEntries(@Param("agentId") String agentId,
                                         @Param("limit") int limit);
}
