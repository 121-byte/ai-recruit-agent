package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import com.example.recruit.dal.mapper.JobProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位画像 CRUD 服务 (复刻对齐清单 §2)。
 * 无 LLM 依赖，仅封装 JobProfileMapper 调用。
 */
@Service
public class JobProfileService {

    private static final Logger log = LoggerFactory.getLogger(JobProfileService.class);

    private final JobProfileMapper jobProfileMapper;

    public JobProfileService(JobProfileMapper jobProfileMapper) {
        this.jobProfileMapper = jobProfileMapper;
    }

    /** 新建岗位画像。 */
    public JobProfile create(JobProfile job) {
        if (job == null) {
            return null;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            if (job.getCreatedAt() == null) {
                job.setCreatedAt(now);
            }
            job.setUpdatedAt(now);
            jobProfileMapper.insert(job);
            return job;
        } catch (Exception e) {
            log.warn("create job failed: {}", e.getMessage());
            return null;
        }
    }

    /** 更新岗位画像。 */
    public boolean update(JobProfile job) {
        if (job == null || job.getId() == null) {
            return false;
        }
        try {
            job.setUpdatedAt(LocalDateTime.now());
            return jobProfileMapper.updateById(job) > 0;
        } catch (Exception e) {
            log.warn("update job failed: {}", e.getMessage());
            return false;
        }
    }

    /** 删除岗位画像。 */
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        try {
            return jobProfileMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.warn("delete job failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按主键查询岗位。 */
    public JobProfile getById(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return jobProfileMapper.selectById(id);
        } catch (Exception e) {
            log.warn("getById job failed: {}", e.getMessage());
            return null;
        }
    }

    /** 查询全部岗位。 */
    public List<JobProfile> listAll() {
        try {
            return jobProfileMapper.selectList(new LambdaQueryWrapper<JobProfile>()
                    .orderByDesc(JobProfile::getCreatedAt));
        } catch (Exception e) {
            log.warn("listAll job failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按状态可选过滤查询岗位 (status 为空时不过滤)。 */
    public List<JobProfile> listByFilter(String status) {
        try {
            return jobProfileMapper.selectByFilter(status);
        } catch (Exception e) {
            log.warn("listByFilter job failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 按状态查询岗位。 */
    public List<JobProfile> listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return listAll();
        }
        try {
            return jobProfileMapper.selectList(new LambdaQueryWrapper<JobProfile>()
                    .eq(JobProfile::getStatus, status)
                    .orderByDesc(JobProfile::getCreatedAt));
        } catch (Exception e) {
            log.warn("listByStatus job failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 更新岗位向量。 */
    public boolean updateEmbedding(Long id, float[] embedding) {
        if (id == null || embedding == null) {
            return false;
        }
        try {
            String literal = FloatVectorTypeHandler.literal(embedding);
            return jobProfileMapper.updateEmbedding(id, literal) > 0;
        } catch (Exception e) {
            log.warn("updateEmbedding job failed: {}", e.getMessage());
            return false;
        }
    }

    /** 多条件搜索/筛选岗位。 */
    public List<JobProfile> search(String keyword, String status, String department, String level) {
        try {
            return jobProfileMapper.search(keyword, status, department, level);
        } catch (Exception e) {
            log.warn("search job failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 获取所有部门列表。 */
    public List<String> listDepartments() {
        try {
            return jobProfileMapper.listDepartments();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 获取所有职级列表。 */
    public List<String> listLevels() {
        try {
            return jobProfileMapper.listLevels();
        } catch (Exception e) {
            return List.of();
        }
    }
}
