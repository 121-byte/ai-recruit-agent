package com.example.recruit.agent.core;

import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;

/**
 * Registers Spring-managed tool beans with AgentScope.
 */
final class ToolRegistrationSupport {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistrationSupport.class);

    private ToolRegistrationSupport() {
    }

    static void register(Toolkit toolkit, Object tool) {
        toolkit.registerTool(targetOf(tool));
    }

    static Object targetOf(Object tool) {
        if (tool instanceof Advised advised) {
            try {
                Object target = advised.getTargetSource().getTarget();
                if (target != null) {
                    log.debug("Unwrapped AOP tool proxy: {} -> {}",
                            tool.getClass().getName(), target.getClass().getName());
                    return target;
                }
            } catch (Exception e) {
                log.warn("unwrap tool proxy failed: {}", e.getMessage());
            }
        }
        return tool;
    }
}
