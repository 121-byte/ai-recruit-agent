package com.example.recruit.agent.tool;

import com.example.recruit.service.CandidateMatchService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 候选人匹配工具 (§8.4)。
 *
 * <p>P1 分层后只做：参数校验 + 调 {@link CandidateMatchService} + 结果摘要。
 * 四阶段匹配逻辑（向量召回/rerank/LLM 评分/透明加权）下沉到 Service，本 Tool 不注入 Mapper、不写业务 SQL。
 */
@Component
public class CandidateMatchingTool {

    private static final int RESULT_LIMIT = 4000;

    private final CandidateMatchService candidateMatchService;

    public CandidateMatchingTool(CandidateMatchService candidateMatchService) {
        this.candidateMatchService = candidateMatchService;
    }

    @Tool(
            name = "matchCandidates",
            description = "为指定岗位匹配候选人，执行四阶段匹配（向量召回+方向过滤+条件rerank+LLM三维评分+透明加权），返回 Top5 候选人并自动创建面试记录。",
            concurrencySafe = false)
    public Map<String, Object> matchCandidates(
            @ToolParam(name = "jobId", description = "岗位 ID（先用 listJobs 查询）")
            Long jobId) {

        if (jobId == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("error", "缺少参数 jobId");
            return r;
        }
        Map<String, Object> result = candidateMatchService.matchForJob(jobId);
        // 结果摘要: 截断避免撑爆 ReAct 上下文
        if (result != null && result.get("candidates") != null) {
            Object cands = result.get("candidates");
            if (cands.toString().length() > RESULT_LIMIT) {
                result.put("candidates_truncated", true);
                result.put("note", "结果已截断, 完整列表见 GET /api/matches/job/" + jobId);
            }
        }
        return result;
    }
}
