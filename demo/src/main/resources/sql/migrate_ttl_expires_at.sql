-- ════════════════════════════════════════════════════════════
-- 存量迁移: 为已存在 memory_entry 回填 ttl_expires_at
-- 艾宾浩斯留存曲线: ttl = base + eff_half_life * ln(1/forget_threshold)
--   eff_half_life = halfLifeDays * (1 + kImportance*importance + kAccess*min(accessCount,10)/10)
-- 默认参数 (与 application.properties app.memory.* 一致):
--   halfLifeDays=60, kImportance=2.0, kAccess=1.5, forgetThreshold=0.3
-- 硬下限: ttl >= created_at + 1 天 (minRetentionDays)
-- 幂等: 仅回填 ttl_expires_at IS NULL 的行
-- ════════════════════════════════════════════════════════════

UPDATE memory_entry
SET ttl_expires_at = GREATEST(
    COALESCE(last_access, created_at, NOW())
        + (60 * (1 + 2.0 * COALESCE(importance, 0.5)
                + 1.5 * LEAST(COALESCE(access_count, 0), 10) / 10.0))
          * (LN(1.0 / 0.3) * INTERVAL '1 day'),
    COALESCE(created_at, NOW()) + INTERVAL '1 day'
)
WHERE ttl_expires_at IS NULL;

-- 上线宽限期 (可选, 首次部署避免冷启动清库): 将所有 ttl 整体后推 7 天
-- UPDATE memory_entry SET ttl_expires_at = ttl_expires_at + INTERVAL '7 days' WHERE ttl_expires_at IS NOT NULL;
