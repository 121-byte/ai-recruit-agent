package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 遗忘机制二：小时级遗忘 (复刻自文档 §5.8，80 行)。
 *
 * <p>定时任务：{@code @Scheduled(cron="0 30 * * * *")} — 每小时 30 分执行。
 *
 * <p>{@link #forget(String)} 三步：
 * <ol>
 *   <li>衰减：14 天未访问的记忆 importance *= 0.8 (跳过 importance ≥ 0.7)</li>
 *   <li>归档：importance &lt; 0.15 的记忆 category = 'archived'</li>
 *   <li>容量限制：活跃记忆 &gt; 200 时，按重要性升序删除最低的
 *       (DELETE ... ORDER BY COALESCE(importance,0.5) ASC, updated_at ASC LIMIT n)</li>
 * </ol>
 *
 * <p>与 {@link MemoryDecayJob} 的对比：本任务更激进 (0.8/14天/0.15阈值/200条容量)，
 * 用于活跃用户的实时遗忘；MemoryDecayJob 是日级全局衰减。
 */
@Component
public class MemoryForgettingService {

    private static final Logger log = LoggerFactory.getLogger(MemoryForgettingService.class);

    private static final double DECAY_FACTOR = 0.8;        // 时级衰减因子
    private static final int DECAY_CUTOFF_DAYS = 14;       // 14 天未访问
    private static final double ARCHIVE_THRESHOLD = 0.15;   // 归档阈值
    private static final int MAX_PER_USER = 200;             // 每用户最多 200 条

    private final MemoryEntryMapper memoryEntryMapper;

    public MemoryForgettingService(MemoryEntryMapper memoryEntryMapper) {
        this.memoryEntryMapper = memoryEntryMapper;
    }

    @Scheduled(cron = "0 30 * * * *")
    public void scheduledForget() {
        log.debug("scheduled forgetting job start");
        // 对每个 distinct agentId 执行遗忘
        try {
            java.util.List<MemoryEntry> all = memoryEntryMapper.selectList(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .select(MemoryEntry::getAgentId));
            java.util.Set<String> agentIds = new java.util.HashSet<>();
            for (MemoryEntry e : all) {
                if (e.getAgentId() != null) {
                    agentIds.add(e.getAgentId());
                }
            }
            for (String agentId : agentIds) {
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

        // Step 3: 容量限制 — 活跃记忆 > 200 时删除最低的
        try {
            Long active = memoryEntryMapper.selectCount(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .eq(MemoryEntry::getAgentId, agentId)
                            .ne(MemoryEntry::getCategory, "archived"));
            long count = active == null ? 0L : active;
            if (count > MAX_PER_USER) {
                int toDelete = (int) (count - MAX_PER_USER);
                int deleted = memoryEntryMapper.deleteLowest(agentId, toDelete);
                log.debug("forget capacity agent={}: deleted={} (was={})", agentId, deleted, count);
            }
        } catch (Exception e) {
            log.warn("forget capacity failed (agent={}): {}", agentId, e.getMessage());
        }
    }
}
