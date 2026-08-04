package com.example.recruit.memory;

import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遗忘清扫器: 动态 TTL 模型 (对齐 hebb-mind 艾宾浩斯留存曲线)。
 *
 * <p>不再做离散衰减 (旧每日 ×0.95 / 小时 ×0.8 已废弃)。续期由 {@link HybridMemoryRetriever}
 * 检索命中时按 eff_half_life 重算 ttl_expires_at;本 Job 只负责清扫:
 * <ul>
 *   <li>{@code deleteExpired()} — 删除 ttl_expires_at &lt; NOW() 且未被高重要度保护的记忆 (全局, 一次);</li>
 *   <li>容量兜底 — 每 agent 活跃记忆 &gt; maxPerUser 时按最低 importance LRU 删除。</li>
 * </ul>
 *
 * <p>cron 当前注解硬编码 (Spring @Scheduled 不读属性);阈值/因子走 {@link AppProperties.Memory},
 * 动态 cron 后续可用 SchedulingConfigurer。
 */
@Component
public class MemoryDecayJob {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayJob.class);

    private final MemoryEntryMapper memoryEntryMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties appProperties;

    public MemoryDecayJob(MemoryEntryMapper memoryEntryMapper, JdbcTemplate jdbcTemplate,
                          AppProperties appProperties) {
        this.memoryEntryMapper = memoryEntryMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.appProperties = appProperties;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void runDailyDecay() {
        AppProperties.Memory mem = appProperties.getMemory();
        log.info("memory ttl sweep start (protectThreshold={}, maxPerUser={})",
                mem.getProtectThreshold(), mem.getMaxPerUser());

        // 1. TTL 清扫 (全局, 一次)
        try {
            int deleted = memoryEntryMapper.deleteExpired(mem.getProtectThreshold());
            log.info("memory ttl sweep: deleted {} expired entries", deleted);
        } catch (Exception e) {
            log.warn("deleteExpired failed: {}", e.getMessage());
        }

        // 2. 容量兜底: 每 agent 活跃记忆超上限时 LRU 删除最低 importance
        Set<String> agentIds = distinctAgentIds();
        for (String agentId : agentIds) {
            try {
                enforceCapacity(agentId, mem.getMaxPerUser());
            } catch (Exception e) {
                log.warn("enforceCapacity failed (agent={}): {}", agentId, e.getMessage());
            }
        }
        log.info("memory ttl sweep done, agents={}", agentIds.size());
    }

    /** 每 agent 活跃记忆 > max 时删除最低 importance 的溢出量。 */
    private void enforceCapacity(String agentId, int maxPerUser) {
        long count = activeCount(agentId);
        if (count <= maxPerUser) {
            return;
        }
        int toDelete = (int) (count - maxPerUser);
        int deleted = memoryEntryMapper.deleteLowest(agentId, toDelete);
        log.debug("capacity trim agent={}: active={} -> deleted {}", agentId, count, deleted);
    }

    private long activeCount(String agentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_entry WHERE agent_id = ? AND category != 'archived'",
                Long.class, agentId);
        return count == null ? 0 : count;
    }

    /** 查询所有 distinct agent_id (HR 隔离)。 */
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
