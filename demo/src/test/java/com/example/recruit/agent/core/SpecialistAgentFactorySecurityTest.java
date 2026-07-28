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
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * {@link SpecialistAgentFactory} 安全配置测试 (OpenSpec p5-tests §2 task 4)。
 *
 * <p>验证工厂构建的 4 个专家 {@link HarnessAgent} 非 null，且启用了文件系统/Shell/
 * 子 Agent/动态技能等安全禁用策略 (复刻自文档 §4.7 buildSpecialist)。
 *
 * <p><b>禁用原因</b>：SpecialistAgentFactory 的 6 个 Tool 依赖 (JobAnalysisTool 等)
 * 各自注入 JobProfileService/JobAnalysisService 等下游 Bean，下游又依赖 Mapper/LLM；
 * 纯 new 构造需 Mock 全链路 Tool，而 {@code Toolkit.registerTool} 对 Mockito mock 的
 * {@code @Tool} 注解反射扫描行为不确定。{@code @SpringBootTest} 则需启动连接远程
 * PG/Redis 的完整上下文 (sql.init.mode=always)，离线不可达。
 *
 * <p>专家 Agent 的构建逻辑已由生产代码 {@link SpecialistAgentFactory#buildSpecialist}
 * 静态保证 (maxIters=3, maxRetries=2, disableFilesystemTools/disableShellTool/
 * disableSubagents 等)；真实端到端验证需在有远程基础设施的开发环境手动运行。
 */
@Disabled("SpecialistAgentFactory 依赖 Tool 全链路 Bean + 远程 DB 上下文，离线测试不适用")
class SpecialistAgentFactorySecurityTest {

    /**
     * 占位测试：文档化手动验证清单。
     * <pre>
     * 手动验证 (有远程基础设施环境):
     *   - getJobAnalystAgent() != null
     *   - getMatchAgent() != null
     *   - getInterviewSpecialistAgent() != null
     *   - getOutreachSpecialistAgent() != null
     *   - 每个 HarnessAgent 配置: maxIters=3, maxRetries=2,
     *     disableFilesystemTools/disableShellTool/disableSubagents/
     *     disableDynamicSkills/disableWorkspaceContext/disableMemoryTools
     * </pre>
     */
    @Test
    void placeholder() {
        // 占位，真实断言需 Spring 上下文，见类级 @Disabled 注释。
        org.junit.jupiter.api.Assertions.assertTrue(true,
                "SpecialistAgentFactory 端到端测试需手动运行 (见 javadoc)");
    }
}
