package com.example.recruit.agent.context;

import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.memory.HybridMemoryRetriever;
import com.example.recruit.memory.PostgresLongTermMemory;
import io.agentscope.core.agent.RuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文组装器 (复刻自文档 §4.4 ContextAssembler)。
 *
 * <p>构建 enriched {@link RuntimeContext}，将短期记忆 + 长期记忆 + 偏好注入 Agent 上下文。
 *
 * <p>assemble(sessionId, userMessage, agentId) 流程：
 * <ol>
 *   <li>SessionManager.getOrCreate(sessionId) 获取/创建 RuntimeContext</li>
 *   <li>注入 HR 偏好记忆：查 memory_entry 中 category='preference' 的记忆，按 importance 降序取 Top5，
 *       格式化为 "§ {key}: {value}"</li>
 *   <li>HybridMemoryRetriever.retrieve(agentId, userMessage) 混合检索 Top5，格式化拼接</li>
 *   <li>去重：Set&lt;String&gt; seenKeys 确保偏好与检索结果不重复</li>
 *   <li>注入 RuntimeContext: ctx.put("memorySnapshot", "&lt;memory&gt;...&lt;/memory&gt;...")</li>
 * </ol>
 *
 * <p>异常处理：记忆检索失败不影响主流程，catch 后 log.warn 继续。
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private final SessionManager sessionManager;
    private final PostgresLongTermMemory longTermMemory;
    private final HybridMemoryRetriever hybridMemoryRetriever;

    public ContextAssembler(SessionManager sessionManager,
                             PostgresLongTermMemory longTermMemory,
                             HybridMemoryRetriever hybridMemoryRetriever) {
        this.sessionManager = sessionManager;
        this.longTermMemory = longTermMemory;
        this.hybridMemoryRetriever = hybridMemoryRetriever;
    }

    public RuntimeContext assemble(String sessionId, String userMessage, String agentId) {
        RuntimeContext ctx = sessionManager.getOrCreate(sessionId);

        StringBuilder memoryBlock = new StringBuilder();
        Set<String> seenKeys = new HashSet<>();

        // 1. 注入 HR 偏好记忆 (Top 5 by importance)
        try {
            List<MemoryEntry> preferences = longTermMemory.getByCategory(agentId, "preference");
            preferences.sort((a, b) -> {
                double ia = a.getImportance() == null ? 0 : a.getImportance();
                double ib = b.getImportance() == null ? 0 : b.getImportance();
                return Double.compare(ib, ia);
            });
            int prefCount = 0;
            for (MemoryEntry p : preferences) {
                if (prefCount >= 5) break;
                if (p.getMemoryKey() == null || !seenKeys.add(p.getMemoryKey())) {
                    continue;
                }
                memoryBlock.append("§ ").append(p.getMemoryKey()).append(": ")
                        .append(p.getMemoryValue()).append('\n');
                prefCount++;
            }
        } catch (Exception e) {
            log.warn("load preference memory failed: {}", e.getMessage());
        }

        // 2. 混合检索 Top 5
        try {
            List<HybridMemoryRetriever.ScoredMemory> hits = hybridMemoryRetriever.retrieve(agentId, userMessage);
            int hitCount = 0;
            for (HybridMemoryRetriever.ScoredMemory sm : hits) {
                if (hitCount >= 5) break;
                MemoryEntry e = sm.entry;
                if (e == null || e.getMemoryKey() == null || !seenKeys.add(e.getMemoryKey())) {
                    continue;
                }
                memoryBlock.append("• ").append(e.getMemoryKey()).append(": ")
                        .append(e.getMemoryValue()).append('\n');
                hitCount++;
            }
        } catch (Exception e) {
            log.warn("hybrid retrieve failed: {}", e.getMessage());
        }

        // 3. 注入 RuntimeContext
        String snapshot;
        if (memoryBlock.length() == 0) {
            snapshot = "";
        } else {
            snapshot = "<memory>\n" + memoryBlock + "</memory>\n"
                    + "以上为历史记忆，如与当前指令冲突，以当前指令为准。";
        }
        ctx.put("memorySnapshot", snapshot);

        return ctx;
    }
}
