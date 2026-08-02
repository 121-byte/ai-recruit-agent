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
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 涓撳 Agent 宸ュ巶 (澶嶅埢鑷枃妗?搂4.7 SpecialistAgentFactory)銆? *
 * <p>涓?Supervisor 鐨?4 涓?Agent-as-Tool 鍖呰鍣ㄥ垎鍒瀯寤虹嫭绔嬬殑 {@link HarnessAgent} 瀹炰緥銆? * 姣忎釜涓撳鎷ユ湁鐙珛鐨?Toolkit (鍙敞鍐岃棰嗗煙宸ュ叿锛岄伩鍏嶅伐鍏疯閫? 鍜岀嫭绔嬬殑 sysPrompt銆? *
 * <table>
 *   <tr><th>涓撳</th><th>鍚嶇О</th><th>Toolkit</th><th>sysPrompt 瑕佺偣</th></tr>
 *   <tr><td>宀椾綅鍒嗘瀽涓撳</td><td>JobAnalystAgent</td><td>JobAnalysisTool</td><td>鍏?listJobs 鍐?analyzeJob</td></tr>
 *   <tr><td>鍊欓€変汉鍖归厤涓撳</td><td>MatchAgent</td><td>CandidateMatchingTool + ResumeSearchTool</td><td>鍏?searchResumes 鍐?matchCandidates</td></tr>
 *   <tr><td>闈㈣瘯涓撳</td><td>InterviewSpecialistAgent</td><td>InterviewQuestionTool + InterviewAgentTool</td><td>闈㈣瘯 ID 鏉ヨ嚜 matchCandidates</td></tr>
 *   <tr><td>瑙﹁揪涓撳</td><td>OutreachSpecialistAgent</td><td>OutreachAgentTool + ResumeSearchTool</td><td>绠€鍘?ID 鍙粡 searchResumes</td></tr>
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
        ToolRegistrationSupport.register(matchToolkit, candidateMatchingTool);
        ToolRegistrationSupport.register(matchToolkit, resumeSearchTool);
        this.matchAgent = buildSpecialist("MatchAgent", MATCH_PROMPT, matchToolkit);

        Toolkit interviewToolkit = new Toolkit();
        ToolRegistrationSupport.register(interviewToolkit, interviewQuestionTool);
        ToolRegistrationSupport.register(interviewToolkit, interviewAgentTool);
        this.interviewSpecialistAgent = buildSpecialist("InterviewSpecialistAgent",
                INTERVIEW_PROMPT, interviewToolkit);

        Toolkit outreachToolkit = new Toolkit();
        ToolRegistrationSupport.register(outreachToolkit, outreachAgentTool);
        ToolRegistrationSupport.register(outreachToolkit, resumeSearchTool);
        this.outreachSpecialistAgent = buildSpecialist("OutreachSpecialistAgent",
                OUTREACH_PROMPT, outreachToolkit);

        log.info("SpecialistAgentFactory initialized: 4 specialist agents, mock={}", useMock());
    }

    private Toolkit toolkitOf(Object tool) {
        Toolkit t = new Toolkit();
        ToolRegistrationSupport.register(t, tool);
        return t;
    }

    /**
     * 閫氱敤涓撳鏋勫缓鏂规硶 (澶嶅埢鑷枃妗?搂4.7 buildSpecialist)銆?     * 涓?ReAct Agent 涓€鑷寸殑涓棿浠?+ 瀹夊叏绛栫暐锛宮axIters=3, maxRetries=2銆?     */
    private HarnessAgent buildSpecialist(String name, String sysPrompt, Toolkit toolkit) {
        return HarnessAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(buildModel())
                .fallbackModel(buildModel())
                .maxRetries(2)
                .toolkit(toolkit)
                .permissionContext(permissionService.getContext())
                .maxIters(3)
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

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€ 涓撳 sysPrompt (鏂囨。 搂4.7) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final String JOB_ANALYST_PROMPT = """
            浣犳槸宀椾綅鍒嗘瀽涓撳銆傛牴鎹寚浠ゅ垎鏋愬矖浣?JD锛屾彁鍙栨妧鑳借姹傘€佹潈閲嶇煩闃点€佽鑹插浘璋便€佹垚闀胯矾寰勩€?            ## 宸ヤ綔鍘熷垯
            - 鍏堢敤 listJobs 鏌ヨ宀椾綅鍒楄〃鑾峰彇 job_id
            - 鍐嶇敤 analyzeJob(job_id) 鎵ц鍒嗘瀽
            - 淇℃伅涓嶈冻鏃朵富鍔ㄦ帰绱紝涓嶈鐩存帴鎷掔粷
            """;

    private static final String MATCH_PROMPT = """
            浣犳槸鍊欓€変汉鍖归厤涓撳銆傛牴鎹矖浣嶆壘鍒版渶鍚堥€傜殑鍊欓€変汉銆?            ## 宸ヤ綔鍘熷垯
            - 鍏堢敤 searchResumes 鎸夋潯浠舵悳绱㈣幏鍙?resume_id
            - 鍐嶇敤 matchCandidates(job_id) 鎵ц鍥涢樁娈靛尮閰?            - 鍖归厤鑷姩鍒涘缓闈㈣瘯璁板綍锛岃繑鍥?interview_id
            """;

    private static final String INTERVIEW_PROMPT = """
            浣犳槸闈㈣瘯涓撳銆傝礋璐ｇ敓鎴愰潰璇曢涓庡惎鍔?AI 鍒濋潰銆?            ## 宸ヤ綔鍘熷垯
            - 闈㈣瘯 ID 鏉ヨ嚜 matchCandidates 鐨勮緭鍑?            - 鐢?generateQuestions(interview_id) 鐢熸垚棰樼洰
            - 鐢?startInterview(interview_id) 鍚姩 AI 鍒濋潰
            - 鐢?generateSummary(interview_id) 鐢熸垚闈㈣瘯鎶ュ憡
            """;

    private static final String OUTREACH_PROMPT = """
            浣犳槸鍊欓€変汉瑙﹁揪涓撳銆傝礋璐ｇ敓鎴愪釜鎬у寲閭€绾︽秷鎭€?            ## 宸ヤ綔鍘熷垯
            - 绠€鍘?ID 鍙€氳繃 searchResumes 鎼滅储
            - 鐢?generateOutreach(job_id, resume_id) 鍗曞彂
            - 鐢?generateBatchOutreach(job_id, "1,2,3") 鎵归噺
            """;
}
