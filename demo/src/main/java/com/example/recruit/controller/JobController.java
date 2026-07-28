package com.example.recruit.controller;

import com.example.recruit.dal.entity.JobProfile;
import com.example.recruit.service.JobAnalysisService;
import com.example.recruit.service.JobProfileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 岗位 API (复刻自文档 §14.4)。
 *
 * <p>GET    /api/jobs                       岗位列表（支持 keyword/status/department/level 筛选）
 * <p>GET    /api/jobs/departments           部门列表
 * <p>GET    /api/jobs/levels                职级列表
 * <p>GET    /api/jobs/{id}                  岗位详情
 * <p>POST   /api/jobs                       创建岗位
 * <p>PUT    /api/jobs/{id}                  更新岗位
 * <p>DELETE /api/jobs/{id}                  删除岗位
 * <p>POST   /api/jobs/{id}/analyze          LLM 分析岗位
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobProfileService jobProfileService;
    private final JobAnalysisService jobAnalysisService;

    public JobController(JobProfileService jobProfileService,
                        JobAnalysisService jobAnalysisService) {
        this.jobProfileService = jobProfileService;
        this.jobAnalysisService = jobAnalysisService;
    }

    @GetMapping
    public List<JobProfile> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String level) {
        try {
            boolean hasParams = (keyword != null && !keyword.isBlank())
                    || (status != null && !status.isBlank())
                    || (department != null && !department.isBlank())
                    || (level != null && !level.isBlank());
            if (hasParams) {
                return jobProfileService.search(keyword, status, department, level);
            }
            return jobProfileService.listAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/departments")
    public List<String> departments() {
        return jobProfileService.listDepartments();
    }

    @GetMapping("/levels")
    public List<String> levels() {
        return jobProfileService.listLevels();
    }

    @GetMapping("/{id}")
    public JobProfile get(@PathVariable Long id) {
        try {
            return jobProfileService.getById(id);
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
            return jobProfileService.create(body);
        } catch (Exception e) {
            return body;
        }
    }

    @PutMapping("/{id}")
    public JobProfile update(@PathVariable Long id, @RequestBody JobProfile body) {
        try {
            body.setId(id);
            jobProfileService.update(body);
        } catch (Exception ignored) {
        }
        return body;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        try {
            jobProfileService.delete(id);
        } catch (Exception ignored) {
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/analyze")
    public Map<String, Object> analyze(@PathVariable Long id) {
        return jobAnalysisService.analyze(id);
    }
}
