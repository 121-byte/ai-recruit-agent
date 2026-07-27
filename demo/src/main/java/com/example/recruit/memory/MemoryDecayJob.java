package com.example.recruit.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.MemoryEntry;
import com.example.recruit.dal.mapper.MemoryEntryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遗忘机制一：每日衰减 (复刻自文档 §5.7，66 行)。
 *
 * <p>定时任务：{@code @Scheduled(cron="0 30 3 * * *")} — 每天凌晨 3:30 执行。
 *
 * <p>对每个 agentId：
 * <ol>
 *   <li>applyDecay(0.95, now-30d)：importance &lt; 0.7 且 30 天前更新的记忆 importance *= 0.95
 *       (importance ≥ 0.7 的高价值记忆豁免)</li>
 *   <li>archiveLowImportance(0.05)：importance &lt; 0.05 的记忆 category = 'archived'</li>
 * </ol>
 */
@Component
public class MemoryDecayJob {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayJob.class);

    private static final double DAILY_DECAY_FACTOR = 0.95;   // 每日衰减因子
    private static final double ARCHIVE_THRESHOLD = 0.05;     // 归档阈值
    private static final int CUTOFF_DAYS = 30;                 // 30 天前的记忆才衰减

    private final MemoryEntryMapper memoryEntryMapper;

    public MemoryDecayJob(MemoryEntryMapper memoryEntryMapper) {
        this.memoryEntryMapper = memoryEntryMapper;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void runDailyDecay() {
        log.info("daily decay job start");
        Set<String> agentIds = distinctAgentIds();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(CUTOFF_DAYS);
        for (String agentId : agentIds) {
            try {
                int decayed = memoryEntryMapper.applyDecay(agentId, DAILY_DECAY_FACTOR, cutoff);
                int archived = memoryEntryMapper.archiveLowImportance(agentId, ARCHIVE_THRESHOLD);
                log.debug("daily decay agent={}: decayed={}, archived={}", agentId, decayed, archived);
            } catch (Exception e) {
                log.warn("daily decay failed (agent={}): {}", agentId, e.getMessage());
            }
        }
        log.info("daily decay job done, agents={}", agentIds.size());
    }

    /** 查询所有 distinct agent_id (HR 隔离)。 */
    private Set<String> distinctAgentIds() {
        try {
            List<MemoryEntry> all = memoryEntryMapper.selectList(
                    new LambdaQueryWrapper<MemoryEntry>()
                            .select(MemoryEntry::getAgentId));
            Set<String> ids = new HashSet<>();
            for (MemoryEntry e : all) {
                if (e.getAgentId() != null) {
                    ids.add(e.getAgentId());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("distinctAgentIds failed: {}", e.getMessage());
            return new HashSet<>();
        }
    }
}
