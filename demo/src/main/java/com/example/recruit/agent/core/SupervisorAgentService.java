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
import com.example.recruit.infra.llm.MockChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Supervisor Agent (澶嶅埢鑷枃妗?搂4.6 SupervisorAgentService)銆? *
 * <p>Supervisor 鏈韩涓嶇洿鎺ユ搷浣滀笟鍔″伐鍏凤紝鑰屾槸灏?4 涓笓瀹?Agent 浣滀负宸ュ叿璋冪敤
 * (Agent-as-Tool 妯″紡)銆? *
 * <p>閰嶇疆宸紓 (涓?ReAct Agent 瀵规瘮)锛? * <ul>
 *   <li>{@code maxIters(5)} 鈥?Supervisor 闇€澶氳疆璋冨害锛岃凯浠ｄ笂闄愭洿楂?/li>
 *   <li>Toolkit 娉ㄥ唽鐨勬槸 Agent-as-Tool 鍖呰鍣?(JobAnalystAgentTool 绛?</li>
 *   <li>sysPrompt 鍖呭惈涓撳璋冪敤椤哄簭涓庝笂涓嬫枃浼犻€掔瓥鐣?/li>
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
        ToolRegistrationSupport.register(toolkit, jobAnalystAgentTool);        // 宀椾綅鍒嗘瀽涓撳
        ToolRegistrationSupport.register(toolkit, matchAgentTool);             // 鍊欓€変汉鍖归厤涓撳
        ToolRegistrationSupport.register(toolkit, interviewSpecialistTool);    // 闈㈣瘯涓撳
        ToolRegistrationSupport.register(toolkit, outreachSpecialistTool);     // 瑙﹁揪涓撳
        ToolRegistrationSupport.register(toolkit, webSearchTool);              // 鑱旂綉鎼滅储

        Model primary = buildModel();
        Model fallback = buildModel();

        this.supervisorAgent = HarnessAgent.builder()
                .name("RecruitmentSupervisor")
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(primary)
                .fallbackModel(fallback)
                .maxRetries(3)
                .toolkit(toolkit)
                .permissionContext(permissionService.getContext())
                .maxIters(5)   // Supervisor 闇€澶氳疆璋冨害
                .middleware(guardrail)
                .middleware(reflexion)
                .disableFilesystemTools()
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableSessionPersistence()
                .disableCompaction()
                .disableMemoryHooks()
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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€ Supervisor sysPrompt (鏂囨。 搂4.6) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final String SUPERVISOR_PROMPT = """
            浣犳槸鎷涜仒鍗忚皟鍛樸€傛牴鎹瓾R鐨勯渶姹傦紝璋冨害涓撳Agent瀹屾垚鍏ㄦ祦绋嬫嫑鑱樸€?            ## 涓撳Agent鍙婅皟鐢ㄩ『搴?            1. jobAnalyst 鈥?鍒嗘瀽JD銆佹彁鍙栨妧鑳界煩闃?            2. matchAgent 鈥?鍖归厤鍊欓€変汉
            3. interviewSpecialist 鈥?鐢熸垚闈㈣瘯棰樻垨鍚姩AI鍒濋潰
            4. outreachSpecialist 鈥?鐢熸垚閭€绾︽秷鎭?            ## 宸ヤ綔鍘熷垯
            - 鎸夋嫑鑱樻祦绋嬮『搴忚皟搴︼細宀椾綅鍒嗘瀽 鈫?鍊欓€変汉鍖归厤 鈫?闈㈣瘯/瑙﹁揪
            - 灏嗗墠涓€涓笓瀹剁殑杈撳嚭缁撴灉浣滀负涓婁笅鏂囧啓鍏ヤ笅涓€涓笓瀹剁殑instruction
            - 鐢ㄦ埛鍙渶鍗曚竴鐜妭鏃剁洿鎺ヨ皟鐢ㄥ搴斾笓瀹讹紝鏃犻渶鍏ㄦ祦绋?            - 淇℃伅涓嶈冻鏃朵富鍔ㄦ帰绱紝鑰岄潪鐩存帴鎷掔粷
            - 瀹屾垚鍚庣敤绠€娲佷腑鏂囧悜HR姹囨姤鍚勭幆鑺傜粨鏋?            """;
}
