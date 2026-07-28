package com.example.recruit.memory;

import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遗忘机制一：每日衰减 (复刻对齐参考 §一-9)。
 *
 * <p>复刻对齐要点：
 * <ul>
 *   <li>SELECT DISTINCT agent_id FROM memory_entry WHERE agent_id LIKE 'hr:%' 用 JdbcTemplate</li>
 *   <li>applyDecay 条件加 last_access + created_at:
 *       importance&lt;0.7 AND (last_access IS NULL OR last_access&lt;cutoff)
 *       AND created_at&lt;now-30d</li>
 *   <li>cutoff 参数类型对齐 (String ISO_LOCAL_DATE)</li>
 * </ul>
 */
@Component
public class MemoryDecayJob {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayJob.class);

    private static final double DAILY_DECAY_FACTOR = 0.95;
    private static final double ARCHIVE_THRESHOLD = 0.05;
    private static final int CUTOFF_DAYS = 30;

    private final MemoryEntryMapper memoryEntryMapper;
    private final JdbcTemplate jdbcTemplate;

    public MemoryDecayJob(MemoryEntryMapper memoryEntryMapper, JdbcTemplate jdbcTemplate) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void runDailyDecay() {
        log.info("daily decay job start");
        Set<String> agentIds = distinctAgentIds();
        String cutoffDate = LocalDate.now().minusDays(CUTOFF_DAYS).toString();
        for (String agentId : agentIds) {
            try {
                int decayed = applyDecay(agentId, DAILY_DECAY_FACTOR, cutoffDate);
                int archived = memoryEntryMapper.archiveLowImportance(agentId, ARCHIVE_THRESHOLD);
                log.debug("daily decay agent={}: decayed={}, archived={}", agentId, decayed, archived);
            } catch (Exception e) {
                log.warn("daily decay failed (agent={}): {}", agentId, e.getMessage());
            }
        }
        log.info("daily decay job done, agents={}", agentIds.size());
    }

    /**
     * applyDecay: importance<0.7 AND (last_access IS NULL OR last_access<cutoff)
     * AND created_at<now-30d AND agent_id=? (对齐参考)。
     * cutoff 为 ISO_LOCAL_DATE 字符串。
     */
    private int applyDecay(String agentId, double factor, String cutoffDate) {
        try {
            return jdbcTemplate.update(
                    "UPDATE memory_entry SET importance = importance * ? " +
                    "WHERE agent_id = ? AND importance < 0.7 " +
                    "AND (last_access IS NULL OR last_access < ?::timestamp) " +
                    "AND created_at < (NOW() - INTERVAL '30 days')",
                    factor, agentId, cutoffDate);
        } catch (Exception e) {
            log.warn("applyDecay failed (agent={}): {}", agentId, e.getMessage());
            return 0;
        }
    }

    /**
     * 查询所有 distinct agent_id (HR 隔离) — 用 JdbcTemplate (对齐参考)。
     */
    private Set<String> distinctAgentIds() {
        try {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT DISTINCT agent_id FROM memory_entry WHERE agent_id LIKE 'hr:%'",
                    String.class);
            return new HashSet<>(ids);
        } catch (Exception e) {
            log.warn("distinctAgentIds failed: {}", e.getMessage());
            return new HashSet<>();
        }
    }
}
