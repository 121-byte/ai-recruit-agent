package com.example.recruit.agent.nudge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.HrPreference;
import com.example.recruit.dal.mapper.HrPreferenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 偏好学习服务 (复刻自文档 §二 nudge/PreferenceLearningService)。
 *
 * <p>从 HR 操作中学习偏好 (如常选的岗位方向、薪资偏好、学校偏好)，
 * 存入 hr_preference 表，供 ContextAssembler / CandidateMatchService 软约束使用。
 */
@Service
public class PreferenceLearningService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceLearningService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HrPreferenceMapper preferenceMapper;

    public PreferenceLearningService(HrPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    /** 记录或更新某 HR 的偏好 JSON。 */
    public void record(Long hrId, String key, Object value) {
        try {
            HrPreference pref = preferenceMapper.selectById(hrId);
            ObjectNode json;
            if (pref == null) {
                pref = new HrPreference();
                pref.setHrId(hrId);
                json = MAPPER.createObjectNode();
            } else {
                json = pref.getPreferenceJson() == null || !pref.getPreferenceJson().isObject()
                        ? MAPPER.createObjectNode()
                        : (ObjectNode) pref.getPreferenceJson();
            }
            json.set(key, MAPPER.valueToTree(value));
            pref.setPreferenceJson(json);
            pref.setUpdatedAt(LocalDateTime.now());
            if (pref.getHrId() != null && preferenceMapper.selectById(hrId) != null) {
                preferenceMapper.updateById(pref);
            } else {
                preferenceMapper.insert(pref);
            }
        } catch (Exception e) {
            log.warn("record preference failed: {}", e.getMessage());
        }
    }

    public HrPreference get(Long hrId) {
        try {
            return preferenceMapper.selectById(hrId);
        } catch (Exception e) {
            return null;
        }
    }
}
