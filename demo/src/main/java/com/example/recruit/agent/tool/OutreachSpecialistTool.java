package com.example.recruit.agent.tool;

import com.example.recruit.agent.core.SpecialistAgentFactory;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 触达专家 Agent-as-Tool 包装器 (复刻自文档 §8.9)。
 */
@Component
public class OutreachSpecialistTool {

    private final SpecialistAgentFactory factory;

    public OutreachSpecialistTool(SpecialistAgentFactory factory) {
        this.factory = factory;
    }

    @Tool(
            name = "outreachSpecialist",
            description = "调用触达专家生成个性化或批量邀约消息。传入自然语言指令。",
            concurrencySafe = false)
    public Map<String, Object> outreachSpecialist(
            @ToolParam(name = "instruction", description = "给触达专家的指令，如 '为岗位1的候选人1生成邀约'")
            String instruction) {
        return JobAnalystAgentTool.callSpecialist("outreachSpecialist", instruction,
                factory.getOutreachSpecialistAgent());
    }
}
