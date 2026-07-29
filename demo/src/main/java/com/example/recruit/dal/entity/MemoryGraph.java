package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 记忆实体之间的关联边。 */
@Data
@TableName("memory_graph")
public class MemoryGraph {

    private Long sourceEntryId;
    private Long targetEntryId;
    private String agentId;
    private String relationType;
    private Double weight;
}
