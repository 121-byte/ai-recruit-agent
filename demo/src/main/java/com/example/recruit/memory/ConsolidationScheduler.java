package com.example.recruit.memory;

import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.ConsolidationTask;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.ConsolidationTaskMapper;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆巩固调度 (复刻对齐参考 §一-7)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>引入 JdbcTemplate 做 DISTINCT 查询、createTask、claimTask</li>
 *   <li>claimTask 乐观锁: UPDATE consolidation_task SET status='processing' WHERE id=? AND status='pending'</li>
 *   <li>候选 SELECT DISTINCT agent_id ... LIMIT 50</li>
 *   <li>阈值 BATCH_THRESHOLD=10</li>
 *   <li>createTask 插 'pending' (非 processing)</li>
 *   <li>保留 triggerCheck / triggerOnSessionEnd 公共方法</li>
 * </ul>
 */
@Component
public class ConsolidationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationScheduler.class);

    private static final int CONSOLIDATION_BATCH = 50;    // 单次最多巩固 50 条 (对齐参考)
    private static final int BATCH_THRESHOLD = 10;        // 低于 10 条不触发 (对齐参考)

    private final MemoryEntryMapper memoryEntryMapper;
    private final ConsolidationTaskMapper consolidationTaskMapper;
    private final MemoryConsolidationAgent consolidationAgent;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsolidationScheduler(MemoryEntryMapper memoryEntryMapper,
                                  ConsolidationTaskMapper consolidationTaskMapper,
                                  MemoryConsolidationAgent consolidationAgent,
                                  AppProperties appProperties,
                                  JdbcTemplate jdbcTemplate) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.consolidationTaskMapper = consolidationTaskMapper;
        this.consolidationAgent = consolidationAgent;
        this.appProperties = appProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 定时触发：每小时整点。 */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledConsolidation() {
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
            // 对齐参考: SELECT DISTINCT agent_id FROM memory_entry
            // WHERE importance IS NULL OR importance=0.5  用 JdbcTemplate
            List<String> agentIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT agent_id FROM memory_entry WHERE importance IS NULL OR importance = 0.5",
                    String.class);
            if (agentIds == null || agentIds.isEmpty()) {
                return;
            }
            for (String agentId : agentIds) {
                if (agentId == null || agentId.isBlank()) {
                    continue;
                }
                try {
                    consolidateBatch(agentId);
                } catch (Exception e) {
                    log.warn("consolidateBatch failed (agent={}): {}", agentId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("triggerCheck failed: {}", e.getMessage());
        }
    }

    /**
     * 单 agent 触发巩固 (对齐参考 triggerOnSessionEnd)。
     */
    public void triggerOnSessionEnd(String agentId) {
        try {
            consolidateBatch(agentId);
        } catch (Exception e) {
            log.warn("triggerOnSessionEnd failed (agent={}): {}", agentId, e.getMessage());
        }
    }

    /** 对单 agent 批量巩固。 */
    private void consolidateBatch(String agentId) {
        // findPendingEntries: LIMIT 50, importance IS NULL OR importance=0.5
        List<MemoryEntry> entries = findPendingEntries(agentId);
        if (entries == null || entries.size() < BATCH_THRESHOLD) {
            return;
        }
        Long taskId = createTask(agentId, entries);
        if (taskId == null) {
            return;
        }
        // claimTask 乐观锁: 未抢到则 skip
        if (!claimTask(taskId)) {
            log.debug("Task {} already claimed by another instance", taskId);
            return;
        }
        try {
            consolidationAgent.consolidate(entries, taskId, agentId);
        } catch (Exception e) {
            log.warn("consolidate failed (agent={}, taskId={}): {}", agentId, taskId, e.getMessage());
        }
    }

    /** 查询待巩固记忆: LIMIT 50, importance IS NULL OR importance=0.5, ORDER BY created_at ASC。 */
    private List<MemoryEntry> findPendingEntries(String agentId) {
        try {
            return memoryEntryMapper.findPendingEntries(agentId, CONSOLIDATION_BATCH);
        } catch (Exception e) {
            log.warn("findPendingEntries failed (agent={}): {}", agentId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 创建一个 pending 状态的巩固任务 (非 processing)，返回其主键。
     * 用 JdbcTemplate INSERT ... RETURNING id。
     */
    private Long createTask(String agentId, List<MemoryEntry> entries) {
        try {
            // 构造 entry_ids 数组字符串: {1,2,3}
            StringBuilder idsStr = new StringBuilder("{");
            boolean first = true;
            for (MemoryEntry e : entries) {
                if (e.getId() != null) {
                    if (!first) idsStr.append(',');
                    idsStr.append(e.getId());
                    first = false;
                }
            }
            idsStr.append('}');
            String entryIdsArr = first ? "{}" : idsStr.toString();

            // JdbcTemplate insert with RETURNING id
            return jdbcTemplate.queryForObject(
                    "INSERT INTO consolidation_task(status, entry_ids, created_at) " +
                    "VALUES('pending', ?::bigint[], NOW()) RETURNING id",
                    Long.class, entryIdsArr);
        } catch (Exception e) {
            log.warn("create consolidation task failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 乐观锁抢占任务: UPDATE consolidation_task SET status='processing' WHERE id=? AND status='pending'。
     * 返回影响行数，0 则 skip (已被其他实例抢占)。
     */
    private boolean claimTask(Long taskId) {
        try {
            int affected = jdbcTemplate.update(
                    "UPDATE consolidation_task SET status = 'processing' WHERE id = ? AND status = 'pending'",
                    taskId);
            return affected > 0;
        } catch (Exception e) {
            log.warn("claimTask failed (taskId={}): {}", taskId, e.getMessage());
            return false;
        }
    }
}
