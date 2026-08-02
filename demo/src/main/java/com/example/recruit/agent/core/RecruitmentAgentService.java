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
 * ReAct Agent (澶嶅埢鑷枃妗?搂4.5 RecruitmentAgentService)銆? *
 * <p>灏佽 AgentScope {@link HarnessAgent}锛屾敞鍐?8 涓笟鍔″伐鍏凤紝閰嶇疆涓棿浠跺拰瀹夊叏绛栫暐銆? *
 * <p>鍏抽敭閰嶇疆 (鏂囨。 搂4.5)锛? * <ul>
 *   <li>{@code maxIters(3)} 鈥?ReAct 鏈€澶氳凯浠?3 娆?/li>
 *   <li>{@code maxRetries(3)} + fallbackModel 鈥?LLM 璋冪敤澶辫触閲嶈瘯涓庨檷绾?/li>
 *   <li>{@code compaction(triggerMessages=20)} 鈥?20 鏉℃秷鎭悗瑙﹀彂涓婁笅鏂囧帇缂?/li>
 *   <li>{@code disable*()} 鈥?绂佺敤鏂囦欢绯荤粺/Shell/瀛怉gent/鍔ㄦ€佹妧鑳?榛樿宸ヤ綔鍖?璁板繂宸ュ叿</li>
 * </ul>
 *
 * <p>sysPrompt 璁捐瑕佺偣锛氭槑纭伐鍏疯皟鐢ㄩ摼璺?(宀椾綅鈫掔畝鍘嗏啋鍖归厤鈫掑嚭棰樷啋闈㈣瘯鈫掕Е杈?锛? * "缂哄皯 ID 鏃跺厛鐢ㄦ悳绱㈠伐鍏锋煡鎵?锛?宸ュ叿杩斿洖 error 鏃惰皟鏁村弬鏁伴噸璇?銆? *
 * <p>Mock 妯″紡锛氭湭閰嶇疆 API Key 鏃剁敤 {@link MockChatModel} 鏇夸唬鐪熷疄妯″瀷锛? * 浣?Agent 璺緞鏃犲瘑閽ヤ篃鑳借繍琛屻€? */
@Service
public class RecruitmentAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentAgentService.class);

    // 鈹€鈹€ 8 涓笟鍔″伐鍏?鈹€鈹€
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
        // 1. 鎵嬪姩鏋勯€?Model 瀵硅薄 (缁曡繃 AgentScope 鐜鍙橀噺妫€鏌? 鏂囨。 搂4.5)
        Model primaryModel = buildModel();
        Model fallbackModel = buildModel();

        Toolkit toolkit = new Toolkit();
        ToolRegistrationSupport.register(toolkit, interviewAgentTool);
        ToolRegistrationSupport.register(toolkit, jobAnalysisTool);
        ToolRegistrationSupport.register(toolkit, candidateMatchingTool);
        ToolRegistrationSupport.register(toolkit, interviewQuestionTool);
        ToolRegistrationSupport.register(toolkit, resumeAnalysisTool);
        ToolRegistrationSupport.register(toolkit, resumeSearchTool);
        ToolRegistrationSupport.register(toolkit, outreachAgentTool);
        ToolRegistrationSupport.register(toolkit, webSearchTool);

        // 3. 鏋勫缓 HarnessAgent
        this.harnessAgent = HarnessAgent.builder()
                .name("RecruitmentAgent")
                .sysPrompt(SYS_PROMPT)
                .model(primaryModel)
                .fallbackModel(fallbackModel)
                .maxRetries(3)
                .toolkit(toolkit)
                .permissionContext(permissionService.getContext())
                .maxIters(3)
                .middleware(conversationGuardrail)   // 杈撳叆鎶ゆ爮
                .middleware(reflexionMiddleware)      // Reflexion 鍙嶆€?                .disableFilesystemTools()             // 绂佺敤 6 绫诲嵄闄╄兘鍔?                .disableShellTool()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableSessionPersistence()
                .disableCompaction()
                .disableMemoryHooks()
                .disableMemoryTools()
                .build();

        log.info("RecruitmentAgent (HarnessAgent) initialized: 8 tools, mock={}", useMock());
    }

    /**
     * 鏋勯€?LLM 妯″瀷锛氭湁 API Key 鐢ㄧ湡瀹?OpenAIChatModel锛屽惁鍒欑敤 MockChatModel銆?     */
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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€ sysPrompt (鏂囨。 搂4.5) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final String SYS_PROMPT = """
            浣犳槸AI鎷涜仒鍔╂墜銆傛牴鎹瓾R鎸囦护璋冪敤宸ュ叿瀹屾垚鎷涜仒鍏ㄦ祦绋嬨€?            ## 宸ュ叿璋冪敤閾捐矾
            1. 宀椾綅锛氱敤 listJobs 鏌ヨ宀椾綅鍒楄〃鑾峰彇 job_id
            2. 绠€鍘嗭細鐢?searchResumes 鎸夊鍚?瀛︽牎/涓撲笟绛夋潯浠舵悳绱㈣幏鍙?resume_id
            3. 鍖归厤锛氱敤 matchCandidates(job_id) 鎵ц鍖归厤锛屽唴閮ㄨ嚜鍔ㄧ敓鎴愰潰璇曡褰?            4. 闈㈣瘯棰橈細鐢?generateQuestions(interview_id) 鐢熸垚棰樼洰
            5. 闈㈣瘯锛氱敤 startInterview(interview_id) 鍚姩AI鍒濋潰
            6. 瑙﹁揪锛氱敤 generateOutreach(job_id, resume_id) 鐢熸垚閭€绾?            ## 琛屼负鍘熷垯
            - 缂哄皯ID鏃跺厛鐢ㄦ悳绱㈠伐鍏锋煡鎵撅紝涓嶈鐩存帴璇?鍋氫笉鍒?
            - 鐢ㄦ埛鎻愬埌濮撳悕/瀛︽牎绛夋潯浠舵椂锛屽厛 searchResumes 鑾峰彇 resume_id
            - 宸ュ叿杩斿洖 error 鏃讹紝鏍规嵁鎻愮ず璋冩暣鍙傛暟閲嶈瘯锛屼笉瑕佹斁寮?            - 瀹屾垚宸ュ叿璋冪敤鍚庯紝鐢ㄧ畝娲佷腑鏂囧悜 HR 姹囨姤缁撴灉
            """;
}
