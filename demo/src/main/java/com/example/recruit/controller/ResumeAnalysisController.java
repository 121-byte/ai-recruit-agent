package com.example.recruit.controller;

import com.example.recruit.domain.analysis.ComparisonResult;
import com.example.recruit.service.ResumeAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 简历分析 API (复刻自对齐清单 §5.9, 独立 Controller, @RequestMapping("/api/resumes"))。
 *
 * <p>2 个权威端点:
 * <ul>
 *   <li>POST /{resumeId}/analyze  全量分析简历</li>
 *   <li>POST /compare            横向对比多份简历</li>
 * </ul>
 *
 * <p>注意: 与 {@link ResumeController} 共享基路径, 但端点不冲突
 * (ResumeController 负责 CRUD, 本 Controller 只管 analyze/compare)。
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;

    public ResumeAnalysisController(ResumeAnalysisService resumeAnalysisService) {
        this.resumeAnalysisService = resumeAnalysisService;
    }

    /** POST /{resumeId}/analyze —— 全量分析简历。 */
    @PostMapping("/{resumeId}/analyze")
    public ResponseEntity<Object> analyze(@PathVariable Long resumeId) {
        return ResponseEntity.ok(resumeAnalysisService.analyzeFull(resumeId));
    }

    /** POST /compare —— 横向对比多份简历 (body {resumeIds:[1,2,3]})。 */
    @PostMapping("/compare")
    public ResponseEntity<ComparisonResult> compare(@RequestBody Map<String, Object> body) {
        Object ids = body.get("resumeIds");
        List<Long> resumeIds = new java.util.ArrayList<>();
        if (ids instanceof List<?> list) {
            for (Object o : list) {
                try {
                    resumeIds.add(Long.parseLong(String.valueOf(o)));
                } catch (Exception ignored) {
                }
            }
        }
        return ResponseEntity.ok(resumeAnalysisService.compareResumes(resumeIds));
    }
}
