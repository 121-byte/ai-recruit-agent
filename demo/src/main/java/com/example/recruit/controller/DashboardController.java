package com.example.recruit.controller;

import com.example.recruit.dal.mapper.CandidateMatchMapper;
import com.example.recruit.dal.mapper.InterviewMapper;
import com.example.recruit.dal.mapper.JobProfileMapper;
import com.example.recruit.dal.mapper.ResumeMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仪表盘 API (复刻自文档 §14.10)。
 *
 * <p>GET /api/dashboard/stats 获取统计概览
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ResumeMapper resumeMapper;
    private final JobProfileMapper jobMapper;
    private final InterviewMapper interviewMapper;
    private final CandidateMatchMapper candidateMatchMapper;

    public DashboardController(ResumeMapper resumeMapper,
                               JobProfileMapper jobMapper,
                               InterviewMapper interviewMapper,
                               CandidateMatchMapper candidateMatchMapper) {
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.interviewMapper = interviewMapper;
        this.candidateMatchMapper = candidateMatchMapper;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("resumes", countSafe(resumeMapper));
        stats.put("jobs", countSafe(jobMapper));
        stats.put("interviews", countSafe(interviewMapper));
        stats.put("matches", countSafe(candidateMatchMapper));
        return stats;
    }

    private long countSafe(Object mapper) {
        try {
            return ((com.baomidou.mybatisplus.core.mapper.BaseMapper<?>) mapper).selectCount(null);
        } catch (Exception e) {
            return 0L;
        }
    }
}
