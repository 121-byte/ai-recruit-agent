package com.example.recruit.agent.context;

import io.agentscope.core.agent.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文快照服务 (复刻对齐清单 §第四部分 agent/context/ContextSnapshotService)。
 *
 * <p>用于 HITL (Human-In-The-Loop) 场景: 当 Agent 需要人工确认时,
 * 将当前 RuntimeContext 快照保存, 等待用户 confirm 后恢复继续执行。
 *
 * <p>当前实现为内存 Map (进程级), P4 可替换为 Redis 持久化。
 */
@Service
public class ContextSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ContextSnapshotService.class);

    private final ConcurrentHashMap<String, RuntimeContext> store = new ConcurrentHashMap<>();

    /** 保存 replyId 对应的 RuntimeContext 快照。 */
    public void save(String replyId, RuntimeContext context) {
        if (replyId == null || context == null) {
            return;
        }
        store.put(replyId, context);
        log.debug("ContextSnapshot saved for replyId={}", replyId);
    }

    /** 恢复 replyId 对应的 RuntimeContext 快照, 不存在返回 null。 */
    public RuntimeContext restore(String replyId) {
        if (replyId == null) {
            return null;
        }
        RuntimeContext ctx = store.get(replyId);
        if (ctx != null) {
            log.debug("ContextSnapshot restored for replyId={}", replyId);
        }
        return ctx;
    }

    /** 移除 replyId 对应的快照。 */
    public void remove(String replyId) {
        if (replyId == null) {
            return;
        }
        store.remove(replyId);
        log.debug("ContextSnapshot removed for replyId={}", replyId);
    }
}
