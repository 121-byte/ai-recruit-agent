package com.example.recruit.controller;

import com.example.recruit.agent.tool.JobAnalysisTool;
import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.dal.mapper.JobProfileMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 岗位 API (复刻自文档 §14.4)。
 *
 * <p>GET    /api/jobs           岗位列表
 * <p>GET    /api/jobs/{id}      岗位详情
 * <p>POST   /api/jobs           创建岗位
 * <p>PUT    /api/jobs/{id}      更新岗位
 * <p>DELETE /api/jobs/{id}      删除岗位
 * <p>POST   /api/jobs/{id}/analyze LLM 分析岗位
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobProfileMapper jobMapper;
    private final JobAnalysisTool jobAnalysisTool;

    public JobController(JobProfileMapper jobMapper, JobAnalysisTool jobAnalysisTool) {
        this.jobMapper = jobMapper;
        this.jobAnalysisTool = jobAnalysisTool;
    }

    @GetMapping
    public List<JobProfile> list() {
        try {
            return jobMapper.selectList(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/{id}")
    public JobProfile get(@PathVariable Long id) {
        try {
            return jobMapper.selectById(id);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    public JobProfile create(@RequestBody JobProfile body) {
        try {
            if (body.getStatus() == null) {
                body.setStatus("draft");
            }
            LocalDateTime now = LocalDateTime.now();
            body.setCreatedAt(now);
            body.setUpdatedAt(now);
            jobMapper.insert(body);
            return body;
        } catch (Exception e) {
            return body;
        }
    }

    @PutMapping("/{id}")
    public JobProfile update(@PathVariable Long id, @RequestBody JobProfile body) {
        try {
            body.setId(id);
            body.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(body);
        } catch (Exception ignored) {
        }
        return body;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        try {
            jobMapper.deleteById(id);
        } catch (Exception ignored) {
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/analyze")
    public Map<String, Object> analyze(@PathVariable Long id) {
        return jobAnalysisTool.analyzeJob(id);
    }
}
