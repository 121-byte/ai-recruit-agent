package com.example.recruit.module.match.api;

import com.example.recruit.dal.entity.CandidateMatch;
import com.example.recruit.module.match.application.CandidateMatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 候选人匹配 API (复刻自对齐清单 §5.5, @RequestMapping("/api/matches"))。
 *
 * <p>复刻由 {@code MatchController} 改名对齐类名, 路径对齐原项目:
 * <ul>
 *   <li>POST                    无参创建匹配记录</li>
 *   <li>POST /job/{jobId}/run   执行岗位匹配</li>
 *   <li>GET  /job/{jobId}       按岗位列出匹配结果</li>
 *   <li>GET  /{id}              匹配详情</li>
 *   <li>POST /{id}/feedback     HR 反馈</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/matches")
public class CandidateMatchController {

    private final CandidateMatchService candidateMatchService;

    public CandidateMatchController(CandidateMatchService candidateMatchService) {
        this.candidateMatchService = candidateMatchService;
    }

    /** POST —— 无参创建匹配记录。 */
    @PostMapping
    public ResponseEntity<CandidateMatch> create(@RequestBody CandidateMatch match) {
        return ResponseEntity.ok(candidateMatchService.create(match));
    }

    /** POST /job/{jobId}/run —— 执行岗位匹配。 */
    @PostMapping("/job/{jobId}/run")
    public ResponseEntity<Map<String, Object>> runMatch(@PathVariable Long jobId) {
        return ResponseEntity.ok(candidateMatchService.matchForJob(jobId));
    }

    /** GET /job/{jobId} —— 按岗位列出匹配结果。 */
    @GetMapping("/job/{jobId}")
    public List<CandidateMatch> listByJob(@PathVariable Long jobId) {
        return candidateMatchService.listByJobId(jobId);
    }

    /** GET /{id} —— 匹配详情。 */
    @GetMapping("/{id}")
    public ResponseEntity<CandidateMatch> get(@PathVariable Long id) {
        return ResponseEntity.of(java.util.Optional.ofNullable(candidateMatchService.getById(id)));
    }

    /** POST /{id}/feedback —— HR 反馈。 */
    @PostMapping("/{id}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String feedback = body.get("feedback") == null ? null : String.valueOf(body.get("feedback"));
        candidateMatchService.feedback(id, feedback);
        return ResponseEntity.ok().build();
    }
}
