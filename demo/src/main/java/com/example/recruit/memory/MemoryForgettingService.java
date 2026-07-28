package com.example.recruit.memory;

import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遗忘机制二：小时级遗忘 (复刻对齐参考 §一-10)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>SELECT DISTINCT agent_id FROM memory_entry WHERE category!='archived' 用 JdbcTemplate</li>
 *   <li>容量删除用 JdbcTemplate DELETE ... WHERE id IN(SELECT ... ORDER BY ... LIMIT ?)</li>
 *   <li>active count 用 JdbcTemplate</li>
 * </ul>
 */
@Component
public class MemoryForgettingService {

    private static final Logger log = LoggerFactory.getLogger(MemoryForgettingService.class);

    private static final double DECAY_FACTOR = 0.8;
    private static final int DECAY_CUTOFF_DAYS = 14;
    private static final double ARCHIVE_THRESHOLD = 0.15;
    private static final int MAX_PER_USER = 200;

    private final MemoryEntryMapper memoryEntryMapper;
    private final JdbcTemplate jdbcTemplate;

    public MemoryForgettingService(MemoryEntryMapper memoryEntryMapper, JdbcTemplate jdbcTemplate) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 30 * * * *")
    public void scheduledForget() {
        log.debug("scheduled forgetting job start");
        try {
            // 对齐参考: SELECT DISTINCT agent_id WHERE category!='archived' 用 JdbcTemplate
            List<String> agentIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT agent_id FROM memory_entry WHERE category != 'archived'",
                    String.class);
            Set<String> idSet = new HashSet<>(agentIds);
            for (String agentId : idSet) {
                try {
                    forget(agentId);
                } catch (Exception e) {
                    log.warn("forget failed (agent={}): {}", agentId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("scheduled forgetting failed: {}", e.getMessage());
        }
    }

    /**
     * 对单个 Agent 执行三步遗忘：衰减 → 归档 → 容量限制。
     */
    public void forget(String agentId) {
        // Step 1: 衰减 — 14 天未访问 * 0.8 (跳过 importance >= 0.7)
        // 对齐参考: cutoff 传 LocalDateTime.toString()
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DECAY_CUTOFF_DAYS);
        try {
            int decayed = memoryEntryMapper.applyDecay(agentId, DECAY_FACTOR, cutoff);
            log.debug("forget decay agent={}: {}", agentId, decayed);
        } catch (Exception e) {
            log.warn("forget decay failed (agent={}): {}", agentId, e.getMessage());
        }

        // Step 2: 归档 — importance < 0.15 的记忆 category = 'archived'
        try {
            int archived = memoryEntryMapper.archiveLowImportance(agentId, ARCHIVE_THRESHOLD);
            log.debug("forget archive agent={}: {}", agentId, archived);
        } catch (Exception e) {
            log.warn("forget archive failed (agent={}): {}", agentId, e.getMessage());
        }

        // Step 3: 容量限制 — 活跃记忆 > 200 时删除最低的 (JdbcTemplate, 对齐参考)
        try {
            long count = activeCount(agentId);
            if (count > MAX_PER_USER) {
                int toDelete = (int) (count - MAX_PER_USER);
                int deleted = deleteCapacity(agentId, toDelete);
                log.debug("forget capacity agent={}: deleted={} (was={})", agentId, deleted, count);
            }
        } catch (Exception e) {
            log.warn("forget capacity failed (agent={}): {}", agentId, e.getMessage());
        }
    }

    /** 活跃记忆数 (JdbcTemplate, 对齐参考)。 */
    private long activeCount(String agentId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM memory_entry WHERE agent_id = ? AND category != 'archived'",
                    Long.class, agentId);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("activeCount failed (agent={}): {}", agentId, e.getMessage());
            return 0L;
        }
    }

    /** 容量删除: JdbcTemplate DELETE ... ORDER BY COALESCE(importance,0.5) ASC, updated_at ASC LIMIT ? (对齐参考)。 */
    private int deleteCapacity(String agentId, int limit) {
        try {
            return jdbcTemplate.update(
                    "DELETE FROM memory_entry WHERE id IN (" +
                    "SELECT id FROM memory_entry WHERE agent_id = ? " +
                    "ORDER BY COALESCE(importance, 0.5) ASC, updated_at ASC LIMIT ?)",
                    agentId, limit);
        } catch (Exception e) {
            log.warn("deleteCapacity failed (agent={}): {}", agentId, e.getMessage());
            return 0;
        }
    }
}
