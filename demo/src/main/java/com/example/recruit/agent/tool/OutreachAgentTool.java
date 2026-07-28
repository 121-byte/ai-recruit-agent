package com.example.recruit.agent.tool;

import com.example.recruit.service.OutreachService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 候选人触达工具 (复刻自文档 §8.7 OutreachAgentTool)。
 *
 * <p>薄封装：参数校验 + 调用 {@link OutreachService} + 结果 truncate。
 * 生成个性化邀约消息，支持单发与批量。
 */
@Component
public class OutreachAgentTool {

    private static final int TRUNCATE = 400;

    private final OutreachService outreachService;

    public OutreachAgentTool(OutreachService outreachService) {
        this.outreachService = outreachService;
    }

    @Tool(
            name = "generateOutreach",
            description = "为单个候选人生成个性化邀约消息（结合岗位亮点 + 候选人背景）。",
            concurrencySafe = false)
    public Map<String, Object> generateOutreach(
            @ToolParam(name = "jobId", description = "岗位 ID")
            Long jobId,
            @ToolParam(name = "resumeId", description = "候选人简历 ID")
            Long resumeId) {
        Map<String, Object> result = outreachService.generateAndCreatePersonalized(jobId, resumeId);
        Object msg = result.get("message");
        if (msg instanceof String s) {
            result.put("message", truncate(s));
        }
        return result;
    }

    @Tool(
            name = "generateBatchOutreach",
            description = "批量生成邀约消息。resumeIds 为逗号分隔的简历 ID。",
            concurrencySafe = false)
    public Map<String, Object> generateBatchOutreach(
            @ToolParam(name = "jobId", description = "岗位 ID")
            Long jobId,
            @ToolParam(name = "resumeIds", description = "简历 ID 列表，逗号分隔，如 \"1,2,3\"")
            String resumeIds) {
        List<Long> ids = Arrays.stream(resumeIds == null ? new String[0] : resumeIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return outreachService.generateAndCreateBatch(jobId, ids == null ? new ArrayList<>() : ids);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > TRUNCATE ? s.substring(0, TRUNCATE) + "..." : s;
    }
}
