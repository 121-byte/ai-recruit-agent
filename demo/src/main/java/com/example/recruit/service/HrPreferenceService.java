package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.HrPreference;
import com.example.recruit.dal.mapper.HrPreferenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * HR 偏好服务 (复刻对齐清单 §2)。
 * 封装 HrPreferenceMapper 的 UPSERT 与过期清理。
 */
@Service
public class HrPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(HrPreferenceService.class);

    private final HrPreferenceMapper hrPreferenceMapper;

    public HrPreferenceService(HrPreferenceMapper hrPreferenceMapper) {
        this.hrPreferenceMapper = hrPreferenceMapper;
    }

    /** 按 HR ID 查询偏好。 */
    public HrPreference getByHrId(Long hrId) {
        if (hrId == null) {
            return null;
        }
        try {
            return hrPreferenceMapper.selectById(hrId);
        } catch (Exception e) {
            log.warn("getByHrId failed: {}", e.getMessage());
            return null;
        }
    }

    /** 保存 (UPSERT) HR 偏好。 */
    public boolean save(HrPreference preference) {
        if (preference == null || preference.getHrId() == null) {
            return false;
        }
        try {
            if (preference.getUpdatedAt() == null) {
                preference.setUpdatedAt(LocalDateTime.now());
            }
            return hrPreferenceMapper.upsert(preference) > 0;
        } catch (Exception e) {
            log.warn("save preference failed: {}", e.getMessage());
            return false;
        }
    }

    /** 清理已过期的 HR 偏好记录。 */
    public int cleanExpired() {
        try {
            return hrPreferenceMapper.deleteExpired();
        } catch (Exception e) {
            log.warn("cleanExpired preference failed: {}", e.getMessage());
            return 0;
        }
    }

    /** 按 HR ID 删除偏好 (兜底清理)。 */
    public boolean deleteByHrId(Long hrId) {
        if (hrId == null) {
            return false;
        }
        try {
            return hrPreferenceMapper.delete(new LambdaQueryWrapper<HrPreference>()
                    .eq(HrPreference::getHrId, hrId)) > 0;
        } catch (Exception e) {
            log.warn("deleteByHrId preference failed: {}", e.getMessage());
            return false;
        }
    }
}
