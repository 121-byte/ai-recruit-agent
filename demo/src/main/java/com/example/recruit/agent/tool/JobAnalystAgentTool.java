package com.example.recruit.agent.tool;

import com.example.recruit.agent.core.SpecialistAgentFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岗位分析专家 Agent-as-Tool 包装器 (复刻自文档 §8.9)。
 *
 * <p>将 JobAnalystAgent 包装为 @Tool，供 Supervisor 调用。
 * Supervisor 传入 instruction 字符串，包装器调用底层专家 Agent 的 call() 执行独立 ReAct 循环。
 */
@Component
public class JobAnalystAgentTool {

    private final SpecialistAgentFactory factory;

    public JobAnalystAgentTool(SpecialistAgentFactory factory) {
        this.factory = factory;
    }

    @Tool(
            name = "jobAnalyst",
            description = "调用岗位分析专家完成 JD 分析、技能矩阵提取。传入自然语言指令。",
            concurrencySafe = false)
    public Map<String, Object> jobAnalyst(
            @ToolParam(name = "instruction", description = "给岗位分析专家的自然语言指令，如 '分析岗位1'")
            String instruction) {
        return callSpecialist("jobAnalyst", instruction, factory.getJobAnalystAgent());
    }

    static Map<String, Object> callSpecialist(String name, String instruction,
                                               io.agentscope.core.agent.Agent specialist) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("specialist", name);
        if (specialist == null) {
            out.put("error", "专家 Agent 未初始化");
            return out;
        }
        try {
            Msg result = specialist.call(instruction).block();
            out.put("result", result == null ? "" : result.getTextContent());
        } catch (Throwable e) {
            out.put("error", "专家调用失败: " + e.getMessage());
        }
        return out;
    }
}
