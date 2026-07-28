package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.ConsolidationTask;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.ConsolidationTaskMapper;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆巩固调度 (复刻自文档 §5.6)。
 *
 * <p>双触发机制：
 * <ol>
 *   <li>定时触发：{@code @Scheduled(cron="0 0 * * * *")} — 每小时整点检查</li>
 *   <li>会话结束触发：{@link #triggerCheck()} — 在会话 doOnComplete 中调用</li>
 * </ol>
 *
 * <p>触发条件：待巩固记忆 (importance IS NULL OR importance &lt; 0.5) ≥ 10 条时，
 * 才执行 LLM 巩固 (避免少量记忆浪费 token)。
 */
@Component
public class ConsolidationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationScheduler.class);

    private static final int CONSOLIDATION_BATCH = 20;   // 单次最多巩固 20 条
    private static final int MIN_TO_TRIGGER = 10;        // 低于 10 条不触发

    private final MemoryEntryMapper memoryEntryMapper;
    private final ConsolidationTaskMapper consolidationTaskMapper;
    private final MemoryConsolidationAgent consolidationAgent;
    private final AppProperties appProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsolidationScheduler(MemoryEntryMapper memoryEntryMapper,
                                  ConsolidationTaskMapper consolidationTaskMapper,
                                  MemoryConsolidationAgent consolidationAgent,
                                  AppProperties appProperties) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.consolidationTaskMapper = consolidationTaskMapper;
        this.consolidationAgent = consolidationAgent;
        this.appProperties = appProperties;
    }

    /** 定时触发：每小时整点。 */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledCheck() {
        try {
            triggerCheck();
        } catch (Exception e) {
            log.warn("scheduled consolidation check failed: {}", e.getMessage());
        }
    }

    /**
     * 会话结束触发：可在会话 doOnComplete 中显式调用。
     * 也可由定时任务每小时调用。
     */
    public void triggerCheck() {
        if (appProperties.useMock()) {
            // Mock 模式下仍尝试 (H2 可工作)；DB 异常会被 try/catch 吞掉
        }
        try {
            List<MemoryEntry> candidates = memoryEntryMapper.selectList(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .isNull(MemoryEntry::getImportance)
                            .or(w -> w.lt(MemoryEntry::getImportance, 0.5))
                            .last("LIMIT " + CONSOLIDATION_BATCH));
            if (candidates == null || candidates.size() < MIN_TO_TRIGGER) {
                return;
            }
            // 按 agentId 分组逐个巩固 (避免跨 Agent 记忆混入同一 prompt)
            java.util.Map<String, List<MemoryEntry>> byAgent = new java.util.HashMap<>();
            for (MemoryEntry e : candidates) {
                byAgent.computeIfAbsent(e.getAgentId(), k -> new java.util.ArrayList<>()).add(e);
            }
            for (java.util.Map.Entry<String, List<MemoryEntry>> en : byAgent.entrySet()) {
                String agentId = en.getKey();
                List<MemoryEntry> entries = en.getValue();
                if (entries.size() < MIN_TO_TRIGGER) {
                    continue;
                }
                Long taskId = createTask(agentId, entries);
                try {
                    consolidationAgent.consolidate(entries, taskId, agentId);
                } catch (Exception e) {
                    log.warn("consolidate failed (agent={}, taskId={}): {}", agentId, taskId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("triggerCheck failed: {}", e.getMessage());
        }
    }

    /** 创建一个 processing 状态的巩固任务，返回其主键。 */
    private Long createTask(String agentId, List<MemoryEntry> entries) {
        try {
            ConsolidationTask task = new ConsolidationTask();
            task.setStatus("processing");
            java.util.List<Long> ids = new java.util.ArrayList<>();
            for (MemoryEntry e : entries) {
                if (e.getId() != null) {
                    ids.add(e.getId());
                }
            }
            task.setEntryIds(ids.toArray(new Long[0]));
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            consolidationTaskMapper.insert(task);
            return task.getId();
        } catch (Exception e) {
            log.warn("create consolidation task failed: {}", e.getMessage());
            return null;
        }
    }
}
