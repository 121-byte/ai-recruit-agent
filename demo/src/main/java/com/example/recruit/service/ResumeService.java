package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.Resume;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.ResumeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历 CRUD 服务 (复刻对齐清单 §2)。
 * 无 LLM 依赖，仅封装 ResumeMapper 调用。
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeMapper resumeMapper;

    public ResumeService(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }

    /** 新建简历。 */
    public Resume create(Resume resume) {
        if (resume == null) {
            return null;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            if (resume.getCreatedAt() == null) {
                resume.setCreatedAt(now);
            }
            resume.setUpdatedAt(now);
            resumeMapper.insert(resume);
            return resume;
        } catch (Exception e) {
            log.warn("create resume failed: {}", e.getMessage());
            return null;
        }
    }

    /** 更新简历。 */
    public boolean update(Resume resume) {
        if (resume == null || resume.getId() == null) {
            return false;
        }
        try {
            resume.setUpdatedAt(LocalDateTime.now());
            return resumeMapper.updateById(resume) > 0;
        } catch (Exception e) {
            log.warn("update resume failed: {}", e.getMessage());
            return false;
        }
    }

    /** 删除简历。 */
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        try {
            return resumeMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.warn("delete resume failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按主键查询简历。 */
    public Resume getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return resumeMapper.selectById(id);
        } catch (Exception e) {
            log.warn("getById resume failed: {}", e.getMessage());
            return null;
        }
    }

    /** 查询全部简历。 */
    public List<Resume> listAll() {
        try {
            return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                    .orderByDesc(Resume::getCreatedAt));
        } catch (Exception e) {
            log.warn("listAll resume failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按状态可选过滤查询简历 (status 为空时不过滤)。 */
    public List<Resume> listByFilter(String status) {
        try {
            return resumeMapper.selectByFilter(status);
        } catch (Exception e) {
            log.warn("listByFilter resume failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按状态查询简历。 */
    public List<Resume> listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return listAll();
        }
        try {
            return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getStatus, status)
                    .orderByDesc(Resume::getCreatedAt));
        } catch (Exception e) {
            log.warn("listByStatus resume failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 更新简历向量。 */
    public boolean updateEmbedding(Long id, float[] embedding) {
        if (id == null || embedding == null) {
            return false;
        }
        try {
            String literal = FloatVectorTypeHandler.literal(embedding);
            return resumeMapper.updateEmbedding(id, literal) > 0;
        } catch (Exception e) {
            log.warn("updateEmbedding resume failed: {}", e.getMessage());
            return false;
        }
    }

    /** 多条件搜索/筛选简历。 */
    public List<Resume> search(String keyword, String status, String intendedPosition, String education) {
        try {
            return resumeMapper.search(keyword, status, intendedPosition, education);
        } catch (Exception e) {
            log.warn("search resume failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 获取所有意向岗位列表。 */
    public List<String> listIntendedPositions() {
        try {
            return resumeMapper.listIntendedPositions();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 获取所有学历列表。 */
    public List<String> listEducations() {
        try {
            return resumeMapper.listEducations();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 多参数可选通用搜索 (复刻自文档 §8.2 ResumeSearchTool)。
     * 所有参数可选，按需组合 WHERE；parsed_json 为 JSONB，like 在 PostgreSQL 下生效。
     */
    public List<Resume> search(String name, String school, String education,
                                 String major, String intendedPosition, Integer minExperience) {
        try {
            LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
                    .eq(name != null && !name.isBlank(), Resume::getCandidateName, name)
                    .like(school != null && !school.isBlank(), Resume::getParsedJson, school)
                    .like(education != null && !education.isBlank(), Resume::getParsedJson, education)
                    .like(major != null && !major.isBlank(), Resume::getParsedJson, major)
                    .like(intendedPosition != null && !intendedPosition.isBlank(), Resume::getParsedJson, intendedPosition)
                    .orderByDesc(Resume::getCreatedAt)
                    .last("LIMIT 20");
            return resumeMapper.selectList(wrapper);
        } catch (Exception e) {
            log.warn("search resume failed: {}", e.getMessage());
            return List.of();
        }
    }
}
