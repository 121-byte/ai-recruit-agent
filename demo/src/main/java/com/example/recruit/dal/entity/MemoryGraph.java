package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 记忆图谱边表 (schema.sql §3.1.5)。复合主键 (source_entry_id, target_entry_id, relation_type)。
 */
@Data
@TableName("memory_graph")
public class MemoryGraph {

    private Long sourceEntryId;

    private Long targetEntryId;

    private String agentId;

    /** 关系类型，默认 related_to */
    private String relationType;

    private Double weight;
}
