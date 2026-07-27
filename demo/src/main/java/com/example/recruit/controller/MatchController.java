package com.example.recruit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.agent.tool.CandidateMatchingTool;
import com.example.recruit.dal.entity.CandidateMatch;
import com.example.recruit.dal.mapper.CandidateMatchMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人匹配 API (复刻自文档 §14.6)。
 *
 * <p>POST /api/matches/{jobId}           执行匹配
 * <p>GET  /api/matches/{jobId}           查看匹配结果
 * <p>GET  /api/matches/{jobId}/sorted    按 overallScore 降序查看匹配结果
 * <p>POST /api/matches/{id}/feedback     HR 反馈
 */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final CandidateMatchingTool candidateMatchingTool;
    private final CandidateMatchMapper candidateMatchMapper;

    public MatchController(CandidateMatchingTool candidateMatchingTool,
                           CandidateMatchMapper candidateMatchMapper) {
        this.candidateMatchingTool = candidateMatchingTool;
        this.candidateMatchMapper = candidateMatchMapper;
    }

    @PostMapping("/{jobId}")
    public Map<String, Object> match(@PathVariable Long jobId) {
        return candidateMatchingTool.matchCandidates(jobId);
    }

    @GetMapping("/{jobId}")
    public List<CandidateMatch> list(@PathVariable Long jobId) {
        try {
            return candidateMatchMapper.selectList(
                    new LambdaQueryWrapper<CandidateMatch>()
                            .eq(CandidateMatch::getJobId, jobId));
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/{jobId}/sorted")
    public List<CandidateMatch> listSorted(@PathVariable Long jobId) {
        try {
            List<CandidateMatch> list = candidateMatchMapper.selectList(
                    new LambdaQueryWrapper<CandidateMatch>()
                            .eq(CandidateMatch::getJobId, jobId));
            list.sort(Comparator.nullsLast(
                    Comparator.comparing(CandidateMatch::getOverallScore,
                            Comparator.nullsLast(Comparator.naturalOrder()))).reversed());
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    @PostMapping("/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        try {
            CandidateMatch cm = candidateMatchMapper.selectById(id);
            if (cm != null) {
                cm.setHrFeedback(feedback);
                candidateMatchMapper.updateById(cm);
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("hrFeedback", feedback);
        return resp;
    }
}
