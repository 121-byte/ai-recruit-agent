package com.example.recruit.agent.core;

import com.example.recruit.agent.middleware.ConversationGuardrail;
import com.example.recruit.agent.middleware.ReflexionMiddleware;
import com.example.recruit.agent.routing.RecruitmentPermissionService;
import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.agent.tool.InterviewAgentTool;
import com.example.recruit.agent.tool.InterviewQuestionTool;
import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.agent.tool.OutreachAgentTool;
import com.example.recruit.agent.tool.ResumeAnalysisTool;
import com.example.recruit.agent.tool.ResumeSearchTool;
import com.example.recruit.agent.tool.WebSearchTool;
import com.example.recruit.config.AppProperties;
import com.example.recruit.llm.MockChatModel;
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
 * ReAct Agent (复刻自文档 §4.5 RecruitmentAgentService)。
 *
 * <p>封装 AgentScope {@link HarnessAgent}，注册 8 个业务工具，配置中间件和安全策略。
 *
 * <p>关键配置 (文档 §4.5)：
 * <ul>
 *   <li>{@code maxIters(3)} — ReAct 最多迭代 3 次</li>
 *   <li>{@code maxRetries(3)} + fallbackModel — LLM 调用失败重试与降级</li>
 *   <li>{@code compaction(triggerMessages=20)} — 20 条消息后触发上下文压缩</li>
 *   <li>{@code disable*()} — 禁用文件系统/Shell/子Agent/动态技能/默认工作区/记忆工具</li>
 * </ul>
 *
 * <p>sysPrompt 设计要点：明确工具调用链路 (岗位→简历→匹配→出题→面试→触达)，
 * "缺少 ID 时先用搜索工具查找"，"工具返回 error 时调整参数重试"。
 *
 * <p>Mock 模式：未配置 API Key 时用 {@link MockChatModel} 替代真实模型，
 * 使 Agent 路径无密钥也能运行。
 */
@Service
public class RecruitmentAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentAgentService.class);

    // ── 8 个业务工具 ──
    private final InterviewAgentTool interviewAgentTool;
    private final JobAnalysisTool jobAnalysisTool;
    private final CandidateMatchingTool candidateMatchingTool;
    private final InterviewQuestionTool interviewQuestionTool;
    private final ResumeAnalysisTool resumeAnalysisTool;
    private final ResumeSearchTool resumeSearchTool;
    private final OutreachAgentTool outreachAgentTool;
    private final WebSearchTool webSearchTool;

    private final ConversationGuardrail conversationGuardrail;
    private final ReflexionMiddleware reflexionMiddleware;
    private final RecruitmentPermissionService permissionService;
    private final AppProperties props;

    private HarnessAgent harnessAgent;

    public RecruitmentAgentService(InterviewAgentTool interviewAgentTool,
                                    JobAnalysisTool jobAnalysisTool,
                                    CandidateMatchingTool candidateMatchingTool,
                                    InterviewQuestionTool interviewQuestionTool,
                                    ResumeAnalysisTool resumeAnalysisTool,
                                    ResumeSearchTool resumeSearchTool,
                                    OutreachAgentTool outreachAgentTool,
                                    WebSearchTool webSearchTool,
                                    ConversationGuardrail conversationGuardrail,
                                    ReflexionMiddleware reflexionMiddleware,
                                    RecruitmentPermissionService permissionService,
                                    AppProperties props) {
        this.interviewAgentTool = interviewAgentTool;
        this.jobAnalysisTool = jobAnalysisTool;
        this.candidateMatchingTool = candidateMatchingTool;
        this.interviewQuestionTool = interviewQuestionTool;
        this.resumeAnalysisTool = resumeAnalysisTool;
        this.resumeSearchTool = resumeSearchTool;
        this.outreachAgentTool = outreachAgentTool;
        this.webSearchTool = webSearchTool;
        this.conversationGuardrail = conversationGuardrail;
        this.reflexionMiddleware = reflexionMiddleware;
        this.permissionService = permissionService;
        this.props = props;
    }

    @PostConstruct
    void init() {
        // 1. 手动构造 Model 对象 (绕过 AgentScope 环境变量检查, 文档 §4.5)
        Model primaryModel = buildModel();
        Model fallbackModel = buildModel();

        // 2. 注册 8 个业务工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(interviewAgentTool);       // AI 面试官
        toolkit.registerTool(jobAnalysisTool);           // 岗位分析
        toolkit.registerTool(candidateMatchingTool);     // 候选人匹配
        toolkit.registerTool(interviewQuestionTool);     // 面试出题
        toolkit.registerTool(resumeAnalysisTool);        // 简历分析
        toolkit.registerTool(resumeSearchTool);          // 通用简历搜索
        toolkit.registerTool(outreachAgentTool);         // 候选人触达
        toolkit.registerTool(webSearchTool);             // 联网搜索

        // 3. 构建 HarnessAgent
        this.harnessAgent = HarnessAgent.builder()
                .name("RecruitmentAgent")
                .sysPrompt(SYS_PROMPT)
                .model(primaryModel)
                .fallbackModel(fallbackModel)
                .maxRetries(3)
                .toolkit(toolkit)
                .memory(MemoryConfig.defaults())
                .permissionContext(permissionService.getContext())
                .maxIters(3)
                .compaction(CompactionConfig.builder().triggerMessages(20).build())
                .middleware(conversationGuardrail)   // 输入护栏
                .middleware(reflexionMiddleware)      // Reflexion 反思
                .disableFilesystemTools()             // 禁用 6 类危险能力
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableMemoryTools()
                .build();

        log.info("RecruitmentAgent (HarnessAgent) initialized: 8 tools, mock={}", useMock());
    }

    /**
     * 构造 LLM 模型：有 API Key 用真实 OpenAIChatModel，否则用 MockChatModel。
     */
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
        if (model == null) {
            return "deepseek-v4-flash";
        }
        return model.startsWith("openai:") ? model.substring("openai:".length()) : model;
    }

    public HarnessAgent getHarnessAgent() {
        return harnessAgent;
    }

    // ─────────────────── sysPrompt (文档 §4.5) ───────────────────

    private static final String SYS_PROMPT = """
            你是AI招聘助手。根据HR指令调用工具完成招聘全流程。
            ## 工具调用链路
            1. 岗位：用 listJobs 查询岗位列表获取 job_id
            2. 简历：用 searchResumes 按姓名/学校/专业等条件搜索获取 resume_id
            3. 匹配：用 matchCandidates(job_id) 执行匹配，内部自动生成面试记录
            4. 面试题：用 generateQuestions(interview_id) 生成题目
            5. 面试：用 startInterview(interview_id) 启动AI初面
            6. 触达：用 generateOutreach(job_id, resume_id) 生成邀约
            ## 行为原则
            - 缺少ID时先用搜索工具查找，不要直接说"做不到"
            - 用户提到姓名/学校等条件时，先 searchResumes 获取 resume_id
            - 工具返回 error 时，根据提示调整参数重试，不要放弃
            - 完成工具调用后，用简洁中文向 HR 汇报结果
            """;
}
