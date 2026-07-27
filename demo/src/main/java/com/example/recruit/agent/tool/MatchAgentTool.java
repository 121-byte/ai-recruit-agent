package com.example.recruit.agent.tool;

import com.example.recruit.agent.core.SpecialistAgentFactory;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 候选人匹配专家 Agent-as-Tool 包装器 (复刻自文档 §8.9)。
 */
@Component
public class MatchAgentTool {

    private final SpecialistAgentFactory factory;

    public MatchAgentTool(SpecialistAgentFactory factory) {
        this.factory = factory;
    }

    @Tool(
            name = "matchAgent",
            description = "调用候选人匹配专家完成候选人检索与匹配。传入自然语言指令。",
            concurrencySafe = false)
    public Map<String, Object> matchAgent(
            @ToolParam(name = "instruction", description = "给匹配专家的指令，如 '为岗位1匹配候选人'")
            String instruction) {
        return JobAnalystAgentTool.callSpecialist("matchAgent", instruction, factory.getMatchAgent());
    }
}
