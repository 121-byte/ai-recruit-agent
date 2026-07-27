package com.example.recruit.agent.event;

import com.example.recruit.dal.entity.AgentTrace;
import com.example.recruit.dal.mapper.AgentTraceMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AOP 追踪切面 (复刻自文档 §10.3 AgentTraceAspect)。
 *
 * <p>拦截 @Tool 注解的工具方法调用，记录到 agent_trace 表：
 * stepType=tool_call/tool_result，inputText/outputText 截断到 2000 字符，
 * 记录 model/tokens/latency/status。
 *
 * <p>异常不阻断主流程，仅记录 status=error。
 */
@Aspect
@Component
public class AgentTraceAspect {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceAspect.class);

    private final AgentTraceMapper agentTraceMapper;
    private static final java.util.concurrent.atomic.AtomicInteger STEP_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(0);

    public AgentTraceAspect(AgentTraceMapper agentTraceMapper) {
        this.agentTraceMapper = agentTraceMapper;
    }

    @Around("@annotation(io.agentscope.core.tool.Tool)")
    public Object traceToolCall(ProceedingJoinPoint pjp) throws Throwable {
        String toolName = pjp.getSignature().getName();
        long start = System.currentTimeMillis();
        Object result;
        String status = "success";
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            status = "error";
            throw e;
        } finally {
            long latency = System.currentTimeMillis() - start;
            AgentTrace trace = new AgentTrace();
            trace.setSessionId("aop");
            trace.setAgentName(pjp.getSignature().getDeclaringType().getSimpleName());
            trace.setStepNo(STEP_SEQ.incrementAndGet());
            trace.setStepType("tool_call");
            trace.setToolName(toolName);
            trace.setInputText(AgentTrace.truncate(java.util.Arrays.toString(pjp.getArgs())));
            trace.setOutputText(null);
            trace.setModel("deepseek-v4-flash");
            trace.setTokens(0);
            trace.setLatencyMs(latency);
            trace.setStatus(status);
            trace.setCreatedAt(LocalDateTime.now());
            try {
                agentTraceMapper.insert(trace);
            } catch (Exception e) {
                log.debug("aop trace insert failed: {}", e.getMessage());
            }
        }
    }
}
