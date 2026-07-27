package com.example.recruit.agent.tool;

import com.example.recruit.agent.core.SpecialistAgentFactory;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 面试专家 Agent-as-Tool 包装器 (复刻自文档 §8.9)。
 */
@Component
public class InterviewSpecialistTool {

    private final SpecialistAgentFactory factory;

    public InterviewSpecialistTool(SpecialistAgentFactory factory) {
        this.factory = factory;
    }

    @Tool(
            name = "interviewSpecialist",
            description = "调用面试专家完成面试出题、AI 初面、面试报告。传入自然语言指令。",
            concurrencySafe = false)
    public Map<String, Object> interviewSpecialist(
            @ToolParam(name = "instruction", description = "给面试专家的指令，如 '为面试1生成面试题'")
            String instruction) {
        return JobAnalystAgentTool.callSpecialist("interviewSpecialist", instruction,
                factory.getInterviewSpecialistAgent());
    }
}
