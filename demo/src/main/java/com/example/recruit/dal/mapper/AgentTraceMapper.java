package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.AgentTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent 追踪表 Mapper。含批量插入与按会话/Agent 聚合查询。
 */
@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTrace> {

    /**
     * 批量插入 Agent 追踪记录 (循环单条 insert)。
     */
    default int batchInsert(List<AgentTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (AgentTrace t : traces) {
            n += insert(t);
        }
        return n;
    }

    /**
     * 按会话 ID 查询全部步骤 (按 step_no 排序)。
     */
    @Select("SELECT * FROM agent_trace WHERE session_id = #{sessionId} ORDER BY step_no")
    List<AgentTrace> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 按 Agent 名称查询步骤记录。
     */
    @Select("SELECT * FROM agent_trace WHERE agent_name = #{agentName} ORDER BY created_at DESC")
    List<AgentTrace> selectByAgentName(@Param("agentName") String agentName);

    /**
     * 按 Agent 名称统计步骤数。
     */
    @Select("SELECT COUNT(*) FROM agent_trace WHERE agent_name = #{agentName}")
    long countByAgentName(@Param("agentName") String agentName);

    /**
     * 统计去重会话数。
     */
    @Select("SELECT COUNT(DISTINCT session_id) FROM agent_trace")
    long countDistinctSessions();

    /**
     * 统计有工具调用的会话数 (tool_name 非空)。
     */
    @Select("SELECT COUNT(DISTINCT session_id) FROM agent_trace WHERE tool_name IS NOT NULL")
    long countSessionsWithToolCalls();

    /**
     * 统计已完成会话数 (存在 text 步骤视为完成)。
     */
    @Select("SELECT COUNT(DISTINCT t.session_id) FROM agent_trace t " +
            "WHERE EXISTS (SELECT 1 FROM agent_trace WHERE session_id = t.session_id AND step_type = 'text')")
    long countCompletedSessions();
}
