package com.example.recruit.agent.core;

import com.example.recruit.agent.middleware.ConversationGuardrail;
import com.example.recruit.agent.middleware.ReflexionMiddleware;
import com.example.recruit.agent.routing.RecruitmentPermissionService;
import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewAgentTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.OutreachAgentTool;
import com.example.recruit.agent.tool.ResumeSearchTool;
import com.example.recruit.config.AppProperties;
import com.example.recruit.infra.llm.MockChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 专家 Agent 工厂 (复刻自文档 §4.7 SpecialistAgentFactory)。
 *
 * <p>为 Supervisor 的 4 个 Agent-as-Tool 包装器分别构建独立的 {@link HarnessAgent} 实例。
 * 每个专家拥有独立的 Toolkit (只注册该领域工具，避免工具误选) 和独立的 sysPrompt。
 *
 * <table>
 *   <tr><th>专家</th><th>名称</th><th>Toolkit</th><th>sysPrompt 要点</th></tr>
 *   <tr><td>岗位分析专家</td><td>JobAnalystAgent</td><td>JobAnalysisTool</td><td>先 listJobs 再 analyzeJob</td></tr>
 *   <tr><td>候选人匹配专家</td><td>MatchAgent</td><td>CandidateMatchingTool + ResumeSearchTool</td><td>先 searchResumes 再 matchCandidates</td></tr>
 *   <tr><td>面试专家</td><td>InterviewSpecialistAgent</td><td>InterviewQuestionTool + InterviewAgentTool</td><td>面试 ID 来自 matchCandidates</td></tr>
 *   <tr><td>触达专家</td><td>OutreachSpecialistAgent</td><td>OutreachAgentTool + ResumeSearchTool</td><td>简历 ID 可经 searchResumes</td></tr>
 * </table>
 */
@Service
public class SpecialistAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(SpecialistAgentFactory.class);

    private final JobAnalysisTool jobAnalysisTool;
    private final CandidateMatchingTool candidateMatchingTool;
    private final ResumeSearchTool resumeSearchTool;
    private final InterviewQuestionTool interviewQuestionTool;
    private final InterviewAgentTool interviewAgentTool;
    private final OutreachAgentTool outreachAgentTool;
    private final ConversationGuardrail guardrail;
    private final ReflexionMiddleware reflexion;
    private final RecruitmentPermissionService permissionService;
    private final AppProperties props;

    private HarnessAgent jobAnalystAgent;
    private HarnessAgent matchAgent;
    private HarnessAgent interviewSpecialistAgent;
    private HarnessAgent outreachSpecialistAgent;

    public SpecialistAgentFactory(JobAnalysisTool jobAnalysisTool,
                                   CandidateMatchingTool candidateMatchingTool,
                                   ResumeSearchTool resumeSearchTool,
                                   InterviewQuestionTool interviewQuestionTool,
                                   InterviewAgentTool interviewAgentTool,
                                   OutreachAgentTool outreachAgentTool,
                                   ConversationGuardrail guardrail,
                                   ReflexionMiddleware reflexion,
                                   RecruitmentPermissionService permissionService,
                                   AppProperties props) {
        this.jobAnalysisTool = jobAnalysisTool;
        this.candidateMatchingTool = candidateMatchingTool;
        this.resumeSearchTool = resumeSearchTool;
        this.interviewQuestionTool = interviewQuestionTool;
        this.interviewAgentTool = interviewAgentTool;
        this.outreachAgentTool = outreachAgentTool;
        this.guardrail = guardrail;
        this.reflexion = reflexion;
        this.permissionService = permissionService;
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.jobAnalystAgent = buildSpecialist("JobAnalystAgent", JOB_ANALYST_PROMPT,
                toolkitOf(jobAnalysisTool));

        Toolkit matchToolkit = new Toolkit();
        matchToolkit.registerTool(candidateMatchingTool);
        matchToolkit.registerTool(resumeSearchTool);
        this.matchAgent = buildSpecialist("MatchAgent", MATCH_PROMPT, matchToolkit);

        Toolkit interviewToolkit = new Toolkit();
        interviewToolkit.registerTool(interviewQuestionTool);
        interviewToolkit.registerTool(interviewAgentTool);
        this.interviewSpecialistAgent = buildSpecialist("InterviewSpecialistAgent",
                INTERVIEW_PROMPT, interviewToolkit);

        Toolkit outreachToolkit = new Toolkit();
        outreachToolkit.registerTool(outreachAgentTool);
        outreachToolkit.registerTool(resumeSearchTool);
        this.outreachSpecialistAgent = buildSpecialist("OutreachSpecialistAgent",
                OUTREACH_PROMPT, outreachToolkit);

        log.info("SpecialistAgentFactory initialized: 4 specialist agents, mock={}", useMock());
    }

    private Toolkit toolkitOf(Object tool) {
        Toolkit t = new Toolkit();
        t.registerTool(tool);
        return t;
    }

    /**
     * 通用专家构建方法 (复刻自文档 §4.7 buildSpecialist)。
     * 与 ReAct Agent 一致的中间件 + 安全策略，maxIters=3, maxRetries=2。
     */
    private HarnessAgent buildSpecialist(String name, String sysPrompt, Toolkit toolkit) {
        return HarnessAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(buildModel())
                .fallbackModel(buildModel())
                .maxRetries(2)
                .toolkit(toolkit)
                .memory(MemoryConfig.defaults())
                .permissionContext(permissionService.getContext())
                .maxIters(3)
                .compaction(CompactionConfig.builder().triggerMessages(20).build())
                .middleware(guardrail)
                .middleware(reflexion)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableMemoryTools()
                .build();
    }

    private Model buildModel() {
        if (useMock()) {
            return new MockChatModel(stripPrefix(props.getAi().getModelPrimary()));
        }
        return OpenAIChatModel.builder()
                .apiKey(props.getAi().getApiKey())
                .baseUrl(props.getAi().getBaseUrl())
                .endpointPath("/chat/completions")
                .modelName(stripPrefix(props.getAi().getModelPrimary()))
                .formatter(new DeepSeekFormatter())
                .stream(true)
                .build();
    }

    private boolean useMock() {
        return props.useMock() || !props.aiKeyPresent();
    }

    private String stripPrefix(String model) {
        if (model == null) return "deepseek-v4-flash";
        return model.startsWith("openai:") ? model.substring("openai:".length()) : model;
    }

    public HarnessAgent getJobAnalystAgent() { return jobAnalystAgent; }
    public HarnessAgent getMatchAgent() { return matchAgent; }
    public HarnessAgent getInterviewSpecialistAgent() { return interviewSpecialistAgent; }
    public HarnessAgent getOutreachSpecialistAgent() { return outreachSpecialistAgent; }

    // ─────────────────── 专家 sysPrompt (文档 §4.7) ───────────────────

    private static final String JOB_ANALYST_PROMPT = """
            你是岗位分析专家。根据指令分析岗位 JD，提取技能要求、权重矩阵、角色图谱、成长路径。
            ## 工作原则
            - 先用 listJobs 查询岗位列表获取 job_id
            - 再用 analyzeJob(job_id) 执行分析
            - 信息不足时主动探索，不要直接拒绝
            """;

    private static final String MATCH_PROMPT = """
            你是候选人匹配专家。根据岗位找到最合适的候选人。
            ## 工作原则
            - 先用 searchResumes 按条件搜索获取 resume_id
            - 再用 matchCandidates(job_id) 执行四阶段匹配
            - 匹配自动创建面试记录，返回 interview_id
            """;

    private static final String INTERVIEW_PROMPT = """
            你是面试专家。负责生成面试题与启动 AI 初面。
            ## 工作原则
            - 面试 ID 来自 matchCandidates 的输出
            - 用 generateQuestions(interview_id) 生成题目
            - 用 startInterview(interview_id) 启动 AI 初面
            - 用 generateSummary(interview_id) 生成面试报告
            """;

    private static final String OUTREACH_PROMPT = """
            你是候选人触达专家。负责生成个性化邀约消息。
            ## 工作原则
            - 简历 ID 可通过 searchResumes 搜索
            - 用 generateOutreach(job_id, resume_id) 单发
            - 用 generateBatchOutreach(job_id, "1,2,3") 批量
            """;
}
