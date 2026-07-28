package com.example.recruit.agent.routing;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import org.springframework.stereotype.Component;

/**
 * HITL 权限引擎 (复刻自文档 §7.3 RecruitmentPermissionService)。
 *
 * <p>构建 AgentScope {@link PermissionContextState}，配置 5 个 HITL 检查点（ASK/ALLOW）。
 * 工具调用时按 toolName 匹配规则：ASK → 触发人工确认；ALLOW → 直放行。
 *
 * <p>规则（按 P1 后实际 Tool 方法对齐）：
 * <ul>
 *   <li>JobAnalysisTool: listJobs / analyzeJob → ALLOW</li>
 *   <li>CandidateMatchingTool: matchCandidates → ASK</li>
 *   <li>InterviewQuestionTool: generateQuestions → ASK / getQuestions → ALLOW</li>
 * </ul>
 */
@Component
public class RecruitmentPermissionService {

    private static final String CATEGORY = "recruit";
    private static final String SOURCE = "recruit";

    private static final PermissionContextState CONTEXT = PermissionContextState.builder()
            .mode(PermissionMode.DEFAULT)
            // JobAnalysisTool 所有方法 ALLOW
            .addAllowRule(CATEGORY, new PermissionRule("listJobs", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("analyzeJob", "", PermissionBehavior.ALLOW, SOURCE))
            // CandidateMatchingTool: matchCandidates ASK (高危, 需 HR 确认)
            .addAskRule(CATEGORY, new PermissionRule("matchCandidates", "", PermissionBehavior.ASK, SOURCE))
            // InterviewQuestionTool: generateQuestions ASK / getQuestions ALLOW
            .addAskRule(CATEGORY, new PermissionRule("generateQuestions", "", PermissionBehavior.ASK, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("getQuestions", "", PermissionBehavior.ALLOW, SOURCE))
            // 其余工具 (searchResumes/analyzeResume/webSearch/generateOutreach/startInterview 等) 默认 ALLOW
            .addAllowRule(CATEGORY, new PermissionRule("searchResumes", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("analyzeResume", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("webSearch", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("generateOutreach", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("generateBatchOutreach", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("startInterview", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("evaluateAnswer", "", PermissionBehavior.ALLOW, SOURCE))
            .addAllowRule(CATEGORY, new PermissionRule("generateSummary", "", PermissionBehavior.ALLOW, SOURCE))
            .build();

    /**
     * 返回 AgentScope 运行时权限上下文。
     */
    public PermissionContextState getContext() {
        return CONTEXT;
    }
}
