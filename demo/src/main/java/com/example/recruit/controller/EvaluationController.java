package com.example.recruit.controller;

import com.example.recruit.dal.entity.EvaluationGoldenSample;
import com.example.recruit.service.EvaluationService;
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
 * 评估 API (复刻自对齐清单 §5.7, @RequestMapping("/api/evaluation"))。
 *
 * <p>5 个权威端点:
 * <ul>
 *   <li>POST /samples          新增金标样本</li>
 *   <li>GET  /samples          查询全部样本</li>
 *   <li>POST /run              执行全量评估</li>
 *   <li>POST /run/{category}   按类别执行评估</li>
 *   <li>GET  /history          历史评估统计</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** POST /samples —— 新增金标样本。 */
    @PostMapping("/samples")
    public ResponseEntity<EvaluationGoldenSample> addSample(@RequestBody Map<String, String> body) {
        String category = body.get("category");
        String input = body.get("input");
        String expected = body.get("expected");
        String criteria = body.get("criteria");
        return ResponseEntity.ok(evaluationService.addSample(category, input, expected, criteria));
    }

    /** GET /samples —— 查询全部样本。 */
    @GetMapping("/samples")
    public List<EvaluationGoldenSample> listSamples() {
        return evaluationService.listSamples();
    }

    /** POST /run —— 执行全量评估。 */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run() {
        return ResponseEntity.ok(evaluationService.runFullEvaluation());
    }

    /** POST /run/{category} —— 按类别执行评估。 */
    @PostMapping("/run/{category}")
    public ResponseEntity<Map<String, Object>> runByCategory(@PathVariable String category) {
        return ResponseEntity.ok(evaluationService.runEvaluationByCategory(category));
    }

    /** GET /history —— 历史评估统计。 */
    @GetMapping("/history")
    public List<Map<String, Object>> history() {
        return evaluationService.getHistoryStats();
    }
}
