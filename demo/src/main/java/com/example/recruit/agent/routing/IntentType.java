package com.example.recruit.agent.routing;

/**
 * 意图类型枚举 (复刻自文档 §4.2)。
 *
 * <p>ConversationAgentService 根据 {@link IntentRouter} 返回的意图类型分流：
 * <ul>
 *   <li>{@link #CHITCHAT} — 闲聊，直答</li>
 *   <li>{@link #SINGLE_TOOL} — 单工具场景，走 ReAct Agent</li>
 *   <li>{@link #COMPOSITE} — 多步骤全流程，走 Supervisor Agent</li>
 *   <li>{@link #HITL} — 人工确认，零 LLM 调用</li>
 *   <li>{@link #BATCH_INDEPENDENT} — 批量独立任务，走 ReWOO 并行执行</li>
 * </ul>
 */
public enum IntentType {
    CHITCHAT,
    SINGLE_TOOL,
    COMPOSITE,
    HITL,
    BATCH_INDEPENDENT
}
