package com.example.recruit.agent.core;

import com.example.recruit.agent.middleware.ConversationGuardrail;
import com.example.recruit.agent.middleware.ReflexionMiddleware;
import com.example.recruit.agent.routing.RecruitmentPermissionService;
import com.example.recruit.agent.tool.InterviewSpecialistTool;
import com.example.recruit.agent.tool.JobAnalystAgentTool;
import com.example.recruit.agent.tool.MatchAgentTool;
import com.example.recruit.agent.tool.OutreachSpecialistTool;
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
 * Supervisor Agent (复刻自文档 §4.6 SupervisorAgentService)。
 *
 * <p>Supervisor 本身不直接操作业务工具，而是将 4 个专家 Agent 作为工具调用
 * (Agent-as-Tool 模式)。
 *
 * <p>配置差异 (与 ReAct Agent 对比)：
 * <ul>
 *   <li>{@code maxIters(5)} — Supervisor 需多轮调度，迭代上限更高</li>
 *   <li>Toolkit 注册的是 Agent-as-Tool 包装器 (JobAnalystAgentTool 等)</li>
 *   <li>sysPrompt 包含专家调用顺序与上下文传递策略</li>
 * </ul>
 */
@Service
public class SupervisorAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentService.class);

    private final JobAnalystAgentTool jobAnalystAgentTool;
    private final MatchAgentTool matchAgentTool;
    private final InterviewSpecialistTool interviewSpecialistTool;
    private final OutreachSpecialistTool outreachSpecialistTool;
    private final WebSearchTool webSearchTool;
    private final ConversationGuardrail guardrail;
    private final ReflexionMiddleware reflexion;
    private final RecruitmentPermissionService permissionService;
    private final AppProperties props;

    private HarnessAgent supervisorAgent;

    public SupervisorAgentService(JobAnalystAgentTool jobAnalystAgentTool,
                                   MatchAgentTool matchAgentTool,
                                   InterviewSpecialistTool interviewSpecialistTool,
                                   OutreachSpecialistTool outreachSpecialistTool,
                                   WebSearchTool webSearchTool,
                                   ConversationGuardrail guardrail,
                                   ReflexionMiddleware reflexion,
                                   RecruitmentPermissionService permissionService,
                                   AppProperties props) {
        this.jobAnalystAgentTool = jobAnalystAgentTool;
        this.matchAgentTool = matchAgentTool;
        this.interviewSpecialistTool = interviewSpecialistTool;
        this.outreachSpecialistTool = outreachSpecialistTool;
        this.webSearchTool = webSearchTool;
        this.guardrail = guardrail;
        this.reflexion = reflexion;
        this.permissionService = permissionService;
        this.props = props;
    }

    @PostConstruct
    void init() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(jobAnalystAgentTool);        // 岗位分析专家
        toolkit.registerTool(matchAgentTool);             // 候选人匹配专家
        toolkit.registerTool(interviewSpecialistTool);    // 面试专家
        toolkit.registerTool(outreachSpecialistTool);     // 触达专家
        toolkit.registerTool(webSearchTool);              // 联网搜索

        Model primary = buildModel();
        Model fallback = buildModel();

        this.supervisorAgent = HarnessAgent.builder()
                .name("RecruitmentSupervisor")
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(primary)
                .fallbackModel(fallback)
                .maxRetries(3)
                .toolkit(toolkit)
                .memory(MemoryConfig.defaults())
                .permissionContext(permissionService.getContext())
                .maxIters(5)   // Supervisor 需多轮调度
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

        log.info("SupervisorAgent initialized: 5 agent-tools, maxIters=5, mock={}", useMock());
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

    public HarnessAgent getSupervisorAgent() {
        return supervisorAgent;
    }

    // ─────────────────── Supervisor sysPrompt (文档 §4.6) ───────────────────

    private static final String SUPERVISOR_PROMPT = """
            你是招聘协调员。根据HR的需求，调度专家Agent完成全流程招聘。
            ## 专家Agent及调用顺序
            1. jobAnalyst — 分析JD、提取技能矩阵
            2. matchAgent — 匹配候选人
            3. interviewSpecialist — 生成面试题或启动AI初面
            4. outreachSpecialist — 生成邀约消息
            ## 工作原则
            - 按招聘流程顺序调度：岗位分析 → 候选人匹配 → 面试/触达
            - 将前一个专家的输出结果作为上下文写入下一个专家的instruction
            - 用户只需单一环节时直接调用对应专家，无需全流程
            - 信息不足时主动探索，而非直接拒绝
            - 完成后用简洁中文向HR汇报各环节结果
            """;
}
