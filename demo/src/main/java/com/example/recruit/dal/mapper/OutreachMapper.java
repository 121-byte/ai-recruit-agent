package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Outreach;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 候选人触达表 Mapper。含批量插入、按批次更新状态与查询。
 */
@Mapper
public interface OutreachMapper extends BaseMapper<Outreach> {

    /**
     * 批量插入触达记录 (循环单条 insert)。
     */
    default int batchInsert(List<Outreach> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Outreach o : items) {
            n += insert(o);
        }
        return n;
    }

    /**
     * 按批次 ID 批量更新状态。
     */
    @Update("UPDATE outreach SET status = #{status} WHERE batch_id = #{batchId}")
    int batchUpdateStatus(@Param("batchId") String batchId,
                          @Param("status") String status);

    /**
     * 按状态统计触达记录数。
     */
    @Select("SELECT COUNT(*) FROM outreach WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 按批次 ID 查询触达记录。
     */
    @Select("SELECT * FROM outreach WHERE batch_id = #{batchId} ORDER BY created_at DESC")
    List<Outreach> selectByBatchId(@Param("batchId") String batchId);
}
